package io.wid.dearby.auth.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.wid.dearby.application.auth.AppleTokenClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

// 애플 토큰 엔드포인트. client secret은 .p8 키로 서명한 ES256 JWT(iss = 팀 ID, sub = client_id, 5분)
@Component
class RestAppleTokenClient(private val properties: AppleProperties, builder: RestClient.Builder) : AppleTokenClient {

    // admin 앱에는 RestClient.Builder 빈이 없어 직접 만든다. 테스트는 MockRestServiceServer를 붙인 builder를 넘긴다
    @Autowired
    constructor(properties: AppleProperties) : this(properties, RestClient.builder())

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = builder.baseUrl(BASE_URL).build()

    // 키 파일은 처음 쓸 때 읽는다. 설정이 없으면 null
    private val key: ECPrivateKey? by lazy {
        val path = properties.privateKeyPath?.takeIf { it.isNotBlank() } ?: return@lazy null
        if (properties.teamId.isNullOrBlank() || properties.keyId.isNullOrBlank()) return@lazy null
        val pem = Files.readString(Path.of(path)).replace(Regex("-----[A-Z ]+-----|\\s"), "")
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))) as ECPrivateKey
    }

    override fun refreshToken(authorizationCode: String, clientId: String): String? {
        val secret = clientSecret(clientId) ?: return null.also { log.warn("Apple key not configured; refresh token not stored") }
        val form = form(clientId, secret, "code" to authorizationCode, "grant_type" to "authorization_code")
        // 웹 방식(Services ID)으로 받은 code는 인가 요청의 redirect_uri와 같아야 한다
        if (clientId == properties.servicesId) properties.callbackUrl?.let { form.add("redirect_uri", it) }
        return try {
            http.post().uri("/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JsonNode::class.java)?.path("refresh_token")?.asString(null)
        } catch (e: RestClientException) {
            log.warn("Apple token exchange failed: {}", e.message)
            null
        }
    }

    override fun revoke(refreshToken: String, clientId: String): Boolean {
        val secret = clientSecret(clientId) ?: return false
        return try {
            http.post().uri("/auth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form(clientId, secret, "token" to refreshToken, "token_type_hint" to "refresh_token"))
                .retrieve().toBodilessEntity()
            true
        } catch (e: RestClientException) {
            log.warn("Apple token revoke failed: {}", e.message)
            false
        }
    }

    fun clientSecret(clientId: String): String? {
        val key = key ?: return null
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.teamId)
            .subject(clientId)
            .audience(AppleTokenDecoder.ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId).build(), claims)
            .apply { sign(ECDSASigner(key)) }.serialize()
    }

    private fun form(clientId: String, secret: String, vararg values: Pair<String, String>) =
        LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", secret)
            values.forEach { (name, value) -> add(name, value) }
        }

    private companion object {
        const val BASE_URL = "https://appleid.apple.com"
    }
}
