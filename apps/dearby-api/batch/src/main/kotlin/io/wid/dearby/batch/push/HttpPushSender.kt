package io.wid.dearby.batch.push

import io.wid.dearby.domain.push.PushPlatform
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

// APNs 토큰 인증(ES256 JWT, HTTP/2)과 FCM HTTP v1(서비스 계정 RS256 JWT → OAuth 접근 토큰). 외부 라이브러리 없이 JDK HttpClient로 보낸다.
// 토큰·인증 정보·응답 본문은 로그에 남기지 않는다(상태 코드와 이유 코드만)
@Component
class HttpPushSender(private val properties: PushProperties, private val json: JsonMapper) : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build()

    private var apnsJwt: Pair<String, Instant>? = null
    private var fcmAccess: Pair<String, Instant>? = null

    override fun problem(platform: PushPlatform): String? = when (platform) {
        PushPlatform.IOS -> when {
            properties.apnsKeyP8.isBlank() || properties.apnsKeyId.isBlank() || properties.apnsTeamId.isBlank() -> "APNS_KEY_P8·APNS_KEY_ID·APNS_TEAM_ID가 없음"
            else -> null
        }
        PushPlatform.ANDROID -> when {
            properties.fcmProjectId.isBlank() || properties.fcmCredentialsPath.isBlank() -> "FCM_PROJECT_ID·FCM_CREDENTIALS_PATH가 없음"
            !Files.isReadable(Path.of(properties.fcmCredentialsPath)) -> "FCM 자격 파일을 읽을 수 없음"
            else -> null
        }
    }

    override fun send(platform: PushPlatform, token: String, message: PushMessage): PushResult = try {
        when (platform) {
            PushPlatform.IOS -> sendApns(token, message)
            PushPlatform.ANDROID -> sendFcm(token, message)
        }
    } catch (e: Exception) {
        log.warn("Push to {} failed: {}", platform, e.javaClass.simpleName)
        PushResult.FAILED
    }

    // APNs: aps.alert{title, body} + activityId(숫자) + url
    private fun sendApns(token: String, message: PushMessage): PushResult {
        val host = if (properties.apnsEnv.equals("production", ignoreCase = true)) "api.push.apple.com" else "api.sandbox.push.apple.com"
        val payload = buildMap<String, Any> {
            put("aps", mapOf("alert" to mapOf("title" to message.title, "body" to message.body), "sound" to "default"))
            message.activityId?.let { put("activityId", it) }
            put("url", message.url)
        }
        val request = HttpRequest.newBuilder(URI.create("https://$host/3/device/$token"))
            .header("authorization", "bearer ${apnsToken()}")
            .header("apns-topic", properties.apnsTopic)
            .header("apns-push-type", "alert")
            .header("apns-priority", "10")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> PushResult.SENT
            410 -> PushResult.UNREGISTERED
            else -> {
                log.warn("APNs rejected a push: {} {}", response.statusCode(), reason(response.body(), "reason"))
                PushResult.FAILED
            }
        }
    }

    // FCM HTTP v1: notification{title, body} + data{activityId, url}(값은 문자열), 안드로이드 채널 new_recruitment
    private fun sendFcm(token: String, message: PushMessage): PushResult {
        val data = buildMap { message.activityId?.let { put("activityId", it.toString()) }; put("url", message.url) }
        val payload = mapOf(
            "message" to mapOf(
                "token" to token,
                "notification" to mapOf("title" to message.title, "body" to message.body),
                "data" to data,
                "android" to mapOf("priority" to "HIGH", "notification" to mapOf("channel_id" to ANDROID_CHANNEL)),
            ),
        )
        val request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/${properties.fcmProjectId}/messages:send"))
            .header("authorization", "Bearer ${fcmToken()}")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return when {
            response.statusCode() == 200 -> PushResult.SENT
            "UNREGISTERED" in response.body() -> PushResult.UNREGISTERED
            else -> {
                log.warn("FCM rejected a push: {} {}", response.statusCode(), reason(response.body(), "status"))
                PushResult.FAILED
            }
        }
    }

    // APNs 인증 JWT는 20분~1시간 사이에 새로 한다(너무 자주 바꾸면 TooManyProviderTokenUpdates)
    @Synchronized
    private fun apnsToken(): String {
        val now = Instant.now()
        apnsJwt?.takeIf { it.second.isAfter(now) }?.let { return it.first }
        val header = mapOf("alg" to "ES256", "kid" to properties.apnsKeyId)
        val claims = mapOf("iss" to properties.apnsTeamId, "iat" to now.epochSecond)
        val key = privateKey(pem(properties.apnsKeyP8), "EC")
        val token = jws(header, claims, key, "SHA256withECDSAinP1363Format") // JWS ES256은 DER이 아닌 R‖S 64바이트
        apnsJwt = token to now.plus(Duration.ofMinutes(45))
        return token
    }

    @Synchronized
    private fun fcmToken(): String {
        val now = Instant.now()
        fcmAccess?.takeIf { it.second.isAfter(now) }?.let { return it.first }
        val account = json.readTree(Files.readString(Path.of(properties.fcmCredentialsPath)))
        val tokenUri = account.path("token_uri").asString("https://oauth2.googleapis.com/token")
        val claims = mapOf(
            "iss" to account.path("client_email").asString(), "scope" to FCM_SCOPE, "aud" to tokenUri,
            "iat" to now.epochSecond, "exp" to now.plusSeconds(3600).epochSecond,
        )
        val assertion = jws(mapOf("alg" to "RS256", "typ" to "JWT"), claims, privateKey(account.path("private_key").asString(), "RSA"), "SHA256withRSA")
        val form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8) + "&assertion=" + assertion
        val response = http.send(
            HttpRequest.newBuilder(URI.create(tokenUri)).header("content-type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(form)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() == 200) { "FCM token request failed: ${response.statusCode()}" }
        val body = json.readTree(response.body())
        val access = body.path("access_token").asString()
        fcmAccess = access to now.plusSeconds(body.path("expires_in").asLong(3600) - 300)
        return access
    }

    private fun jws(header: Map<String, Any>, claims: Map<String, Any>, key: PrivateKey, algorithm: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(json.writeValueAsBytes(header)) + "." + encoder.encodeToString(json.writeValueAsBytes(claims))
        val signature = Signature.getInstance(algorithm).apply { initSign(key); update(input.toByteArray(StandardCharsets.US_ASCII)) }.sign()
        return input + "." + encoder.encodeToString(signature)
    }

    // PEM 내용 또는 파일 경로
    private fun pem(value: String): String = if ("-----BEGIN" in value) value else Files.readString(Path.of(value))

    private fun privateKey(pem: String, algorithm: String): PrivateKey {
        val body = pem.replace("\\n", "\n").lines().filterNot { it.startsWith("-----") }.joinToString("").trim()
        return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(body)))
    }

    // 응답의 이유 코드만(본문에 토큰이 되돌아올 수 있어 전부 남기지 않는다)
    private fun reason(body: String, field: String): String =
        runCatching { json.readTree(body).let { it.path(field).asString(it.path("error").path(field).asString("")) } }.getOrDefault("")

    private companion object {
        const val ANDROID_CHANNEL = "new_recruitment"
        const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
