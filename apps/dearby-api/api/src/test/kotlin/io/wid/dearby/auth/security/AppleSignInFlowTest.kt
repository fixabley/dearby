package io.wid.dearby.auth.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import io.wid.dearby.application.auth.AppleSignInService
import io.wid.dearby.application.auth.AppleTokenClient
import io.wid.dearby.application.auth.TokenService
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 애플 공개키 대신 테스트 키로 서명한 identity token을 쓴다. 검증 규칙(iss·aud·exp·nonce)은 운영과 같다
@SpringBootTest(
    properties = [
        "dearby.apple.client-ids=io.wid.dearby,io.wid.dearby.signin",
        "dearby.apple.services-id=io.wid.dearby.signin",
        "dearby.apple.callback-url=https://api.example.test/v1/auth/apple/callback",
        "dearby.google.client-ids=web-client.apps.googleusercontent.com, ios-client.apps.googleusercontent.com",
    ]
)
@AutoConfigureMockMvc
class AppleSignInFlowTest(
    @Autowired val mvc: MockMvc,
    @Autowired val jdbc: JdbcClient,
    @Autowired val tokens: TokenService,
) {
    @TestConfiguration
    class AppleKeys {
        @Bean
        @Primary
        fun testAppleTokenDecoder(): AppleTokenDecoder =
            AppleTokenDecoder.of(NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build(), listOf("io.wid.dearby", "io.wid.dearby.signin"))

        // 구글 공개키 대신 같은 테스트 키로 서명한 ID 토큰을 받는다. 허용 aud 규칙은 운영과 같다(native-v1.md "구글 로그인 — 네이티브")
        @Bean
        @Primary
        fun testGoogleTokenDecoder(): GoogleTokenDecoder =
            GoogleTokenDecoder.of(
                NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build(),
                listOf("web-client.apps.googleusercontent.com", "ios-client.apps.googleusercontent.com"),
            )

        // 애플 토큰 엔드포인트 대신 code를 그대로 담은 토큰을 준다
        @Bean
        @Primary
        fun fakeAppleTokenClient() = object : AppleTokenClient {
            override fun refreshToken(authorizationCode: String, clientId: String) = "rt-$clientId-$authorizationCode"
            override fun revoke(refreshToken: String, clientId: String) = true
        }
    }

    private val json = JsonMapper.builder().build()

    private fun appleToken(
        subject: String,
        nonce: String?,
        audience: String = "io.wid.dearby",
        issuer: String = AppleTokenDecoder.ISSUER,
        expiresAt: Instant = Instant.now().plusSeconds(600),
    ): String {
        val claims = JwtClaimsSet.builder().issuer(issuer).audience(listOf(audience)).subject(subject)
            .issuedAt(expiresAt.minusSeconds(1200)).expiresAt(expiresAt).claim("email", "$subject@privaterelay.appleid.com")
            .apply { nonce?.let { claim("nonce", it) } }.build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.keyID).build()
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    // 구글 ID 토큰: 애플과 달리 nonce는 해시 없이 그대로, email_verified가 있어야 email을 남긴다
    private fun googleToken(
        subject: String,
        nonce: String?,
        audience: String = "web-client.apps.googleusercontent.com",
        issuer: String = "https://accounts.google.com",
        expiresAt: Instant = Instant.now().plusSeconds(600),
        emailVerified: Boolean = true,
    ): String {
        val claims = JwtClaimsSet.builder().issuer(issuer).audience(listOf(audience)).subject(subject)
            .issuedAt(expiresAt.minusSeconds(1200)).expiresAt(expiresAt)
            .claim("email", "$subject@gmail.com").claim("email_verified", emailVerified).claim("name", " 조민준 ")
            .apply { nonce?.let { claim("nonce", it) } }.build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.keyID).build()
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun googleNonce(): String = read(post("/v1/auth/google/nonce", "").andExpect { status { isOk() } })["nonce"].asString()

    private fun googleSignIn(subject: String, token: (String) -> String = { googleToken(subject, it) }): ResultActionsDsl {
        val nonce = googleNonce()
        return post("/v1/auth/google", """{"idToken":"${token(nonce)}","nonce":"$nonce"}""")
    }

    private fun post(path: String, body: String, bearer: String? = null) = mvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = body
        bearer?.let { header("Authorization", "Bearer $it") }
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun nonce(): Pair<String, String> = read(post("/v1/auth/apple/nonce", "").andExpect { status { isOk() } })
        .let { it["challengeId"].asString() to it["nonce"].asString() }

    private fun signIn(subject: String, displayName: String? = null, code: String? = null): ResultActionsDsl {
        val (challengeId, nonce) = nonce()
        val name = displayName?.let { ""","displayName":"$it"""" } ?: ""
        val authorization = code?.let { ""","authorizationCode":"$it"""" } ?: ""
        return post("/v1/auth/apple", """{"challengeId":"$challengeId","identityToken":"${appleToken(subject, nonce)}"$name$authorization}""")
    }

    private fun storedToken(subject: String) =
        jdbc.sql("select client_id || '|' || refresh_token from user_identities where subject = ?").param(subject).query(String::class.java).single()

    private fun subject() = "apple-${UUID.randomUUID()}"

    @Test
    fun `처음 보는 애플 계정은 linkToken을 받고, 새 계정을 만들면 다음부터 바로 로그인한다`() {
        val subject = subject()
        val first = read(signIn(subject, " 조민준 ", code = "c1").andExpect { status { isAccepted() } })
        assertEquals("$subject@privaterelay.appleid.com", first["email"].asString())

        val linkToken = first["linkToken"].asString()
        val created = read(post("/v1/auth/apple/register", """{"linkToken":"$linkToken"}""").andExpect { status { isCreated() } })
        // linkToken은 한 번만 쓴다
        post("/v1/auth/apple/register", """{"linkToken":"$linkToken"}""").andExpect { status { isUnauthorized() } }

        val again = read(signIn(subject).andExpect { status { isOk() } })
        assertEquals(created["user"]["id"].asString(), again["user"]["id"].asString())
        assertEquals(listOf("USER"), again["user"]["roles"].values().map { it.asString() })
        // authorization code로 받은 애플 토큰을 남겨 계정 삭제 때 폐기한다. 다시 로그인하면 새 토큰으로 바꾼다
        assertEquals("io.wid.dearby|rt-io.wid.dearby-c1", storedToken(subject))
        signIn(subject, code = "c2").andExpect { status { isOk() } }
        assertEquals("io.wid.dearby|rt-io.wid.dearby-c2", storedToken(subject))
        assertEquals("조민준", jdbc.sql("select display_name from user_identities where subject = ?").param(subject).query(String::class.java).single())
    }

    @Test
    fun `패스키 계정에 로그인한 상태로 연결하면 그 계정으로 로그인된다`() {
        val user = User(UUID.randomUUID(), listOf(UserRole.USER))
        jdbc.sql("insert into users (id) values (?)").param(user.id).update()
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(user.id).update()
        val access = tokens.issue(user).accessToken
        val subject = subject()

        val linkToken = read(signIn(subject).andExpect { status { isAccepted() } })["linkToken"].asString()
        // 로그인하지 않으면 연결할 수 없다
        post("/v1/me/identities/apple", """{"linkToken":"$linkToken"}""").andExpect { status { isUnauthorized() } }
        post("/v1/me/identities/apple", """{"linkToken":"$linkToken"}""", access).andExpect { status { isNoContent() } }
        assertEquals(user.id.toString(), read(signIn(subject).andExpect { status { isOk() } })["user"]["id"].asString())

        // 이미 애플 계정이 있는 사용자에는 다른 애플 계정을 연결하지 않는다
        val otherLink = read(signIn(subject()).andExpect { status { isAccepted() } })["linkToken"].asString()
        post("/v1/me/identities/apple", """{"linkToken":"$otherLink"}""", access).andExpect { status { isBadRequest() } }
        // 이미 연결된 애플 계정은 linkToken 없이 바로 로그인하므로, 다른 사용자로 옮길 길이 없다
        assertEquals(1, jdbc.sql("select count(*) from user_identities where user_id = ?").param(user.id).query(Int::class.java).single())
    }

    @Test
    fun `서명·발급자·대상·만료·nonce가 맞지 않으면 401이고 challenge는 다시 쓸 수 없다`() {
        val subject = subject()
        fun attempt(token: (String) -> String): ResultActionsDsl {
            val (challengeId, nonce) = nonce()
            return post("/v1/auth/apple", """{"challengeId":"$challengeId","identityToken":"${token(nonce)}"}""")
        }
        attempt { appleToken(subject, it, audience = "com.example.other") }.andExpect { status { isUnauthorized() } }
        attempt { appleToken(subject, it, issuer = "https://example.com") }.andExpect { status { isUnauthorized() } }
        attempt { appleToken(subject, it, expiresAt = Instant.now().minusSeconds(3600)) }.andExpect { status { isUnauthorized() } }
        attempt { appleToken(subject, "other-nonce") }.andExpect { status { isUnauthorized() } }
        attempt { appleToken(subject, null) }.andExpect { status { isUnauthorized() } }
        attempt { "not-a-jwt" }.andExpect { status { isUnauthorized() } }
        // Services ID 대상(Android·웹)도 받는다
        attempt { appleToken(subject, it, audience = "io.wid.dearby.signin") }.andExpect { status { isAccepted() } }

        val (challengeId, nonce) = nonce()
        val body = """{"challengeId":"$challengeId","identityToken":"${appleToken(subject, nonce)}"}"""
        post("/v1/auth/apple", body).andExpect { status { isAccepted() } }
        post("/v1/auth/apple", body).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `웹 방식은 콜백이 1회용 코드로 앱에 돌려주고, verifier를 가진 앱만 바꿔 간다`() {
        val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"
        val authorize = read(post("/v1/auth/apple/web/start", """{"verifierHash":"${AppleSignInService.sha256(verifier)}"}""").andExpect { status { isOk() } })["authorizeUrl"].asString()
        val query = UriComponentsBuilder.fromUriString(authorize).build().queryParams
        assertEquals("io.wid.dearby.signin", query.getFirst("client_id"))
        assertEquals("form_post", query.getFirst("response_mode"))
        val state = query.getFirst("state")!!
        val nonce = query.getFirst("nonce")!!
        val subject = subject()

        fun callback(stateValue: String) = mvc.post("/v1/auth/apple/callback") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("state", stateValue)
            param("id_token", appleToken(subject, nonce, audience = "io.wid.dearby.signin"))
            param("user", """{"name":{"firstName":"Minjun","lastName":"Jo"},"email":"x@privaterelay.appleid.com"}""")
            param("code", "web-code")
        }.andExpect { status { isSeeOther() } }.andReturn().response.getHeader("Location")!!

        val location = callback(state)
        assertTrue(location.startsWith("dearby://auth/apple?code="), location)
        val code = UriComponentsBuilder.fromUriString(location).build().queryParams.getFirst("code")!!
        // 같은 state를 다시 쓰면 실패를 돌려준다
        assertEquals("dearby://auth/apple?error=failed", callback(state))

        val linkToken = read(post("/v1/auth/apple/web/exchange", """{"code":"$code","verifier":"$verifier"}""").andExpect { status { isAccepted() } })["linkToken"].asString()
        post("/v1/auth/apple/register", """{"linkToken":"$linkToken"}""").andExpect { status { isCreated() } }
        // 웹 방식은 Services ID로 받은 code라 그 client_id로 토큰을 받는다
        assertEquals("io.wid.dearby.signin|rt-io.wid.dearby.signin-web-code", storedToken(subject))
        // 코드도 한 번만 쓴다
        post("/v1/auth/apple/web/exchange", """{"code":"$code","verifier":"$verifier"}""").andExpect { status { isUnauthorized() } }

        // 다른 verifier로는 바꿔 갈 수 없다
        val other = read(post("/v1/auth/apple/web/start", """{"verifierHash":"${AppleSignInService.sha256(verifier)}"}"""))["authorizeUrl"].asString()
        val otherQuery = UriComponentsBuilder.fromUriString(other).build().queryParams
        val otherLocation = mvc.post("/v1/auth/apple/callback") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("state", otherQuery.getFirst("state")!!)
            param("id_token", appleToken(subject, otherQuery.getFirst("nonce")!!, audience = "io.wid.dearby.signin"))
        }.andReturn().response.getHeader("Location")!!
        val otherCode = UriComponentsBuilder.fromUriString(otherLocation).build().queryParams.getFirst("code")!!
        post("/v1/auth/apple/web/exchange", """{"code":"$otherCode","verifier":"wrong"}""").andExpect { status { isUnauthorized() } }

        // 사용자가 취소하면 앱에 cancelled를 알린다
        val cancelled = mvc.post("/v1/auth/apple/callback") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("error", "user_cancelled_authorize")
        }.andReturn().response.getHeader("Location")
        assertEquals("dearby://auth/apple?error=cancelled", cancelled)
        post("/v1/auth/apple/web/start", """{"verifierHash":"short"}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `구글 네이티브 — 처음 보는 계정은 linkToken을 받고, 새 계정이나 패스키 계정 연결 뒤에는 바로 로그인한다`() {
        val subject = "google-${UUID.randomUUID()}"
        val first = read(googleSignIn(subject).andExpect { status { isAccepted() } })
        assertEquals("$subject@gmail.com", first["email"].asString())
        assertEquals("조민준", first["displayName"].asString())
        val linkToken = first["linkToken"].asString()
        val created = read(post("/v1/auth/google/register", """{"linkToken":"$linkToken"}""").andExpect { status { isCreated() } })
        post("/v1/auth/google/register", """{"linkToken":"$linkToken"}""").andExpect { status { isUnauthorized() } }
        // iOS SDK가 보내는 iOS 클라이언트 aud도 같은 계정으로 로그인한다
        val again = read(googleSignIn(subject) { googleToken(subject, it, audience = "ios-client.apps.googleusercontent.com") }.andExpect { status { isOk() } })
        assertEquals(created["user"]["id"].asString(), again["user"]["id"].asString())
        assertEquals(1, jdbc.sql("select count(*) from user_identities where provider = 'GOOGLE' and subject = ? and refresh_token is null").param(subject).query(Int::class.java).single())

        // 패스키 계정에 연결: 로그인 필요, 한 계정에 구글 계정은 하나
        val user = User(UUID.randomUUID(), listOf(UserRole.USER))
        jdbc.sql("insert into users (id) values (?)").param(user.id).update()
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(user.id).update()
        val access = tokens.issue(user).accessToken
        val other = "google-${UUID.randomUUID()}"
        val link = read(googleSignIn(other).andExpect { status { isAccepted() } })["linkToken"].asString()
        post("/v1/me/identities/google", """{"linkToken":"$link"}""").andExpect { status { isUnauthorized() } }
        post("/v1/me/identities/google", """{"linkToken":"$link"}""", access).andExpect { status { isNoContent() } }
        assertEquals(user.id.toString(), read(googleSignIn(other).andExpect { status { isOk() } })["user"]["id"].asString())
        val third = read(googleSignIn("google-${UUID.randomUUID()}").andExpect { status { isAccepted() } })["linkToken"].asString()
        post("/v1/me/identities/google", """{"linkToken":"$third"}""", access).andExpect { status { isBadRequest() } }
        // 이미 다른 계정(created)에 연결된 구글 계정은 이 계정에 연결할 수 없다. 로그인은 바로 되므로 linkToken이 나오지 않는다
        googleSignIn(subject).andExpect { status { isOk() } }
    }

    @Test
    fun `구글 네이티브 — 서명·발급자·대상·만료·nonce가 맞지 않으면 401이고 nonce는 한 번만 쓰며 인증되지 않은 이메일은 남기지 않는다`() {
        val subject = "google-${UUID.randomUUID()}"
        googleSignIn(subject) { googleToken(subject, it, audience = "other-client.apps.googleusercontent.com") }.andExpect { status { isUnauthorized() } }
        googleSignIn(subject) { googleToken(subject, it, issuer = "https://example.com") }.andExpect { status { isUnauthorized() } }
        googleSignIn(subject) { googleToken(subject, it, expiresAt = Instant.now().minusSeconds(3600)) }.andExpect { status { isUnauthorized() } }
        googleSignIn(subject) { googleToken(subject, "other-nonce") }.andExpect { status { isUnauthorized() } }
        googleSignIn(subject) { googleToken(subject, null) }.andExpect { status { isUnauthorized() } }
        googleSignIn(subject) { "not-a-jwt" }.andExpect { status { isUnauthorized() } }
        // 서버가 주지 않은 nonce, 애플 nonce는 받지 않는다
        post("/v1/auth/google", """{"idToken":"${googleToken(subject, "made-up")}","nonce":"made-up"}""").andExpect { status { isUnauthorized() } }
        val (_, appleNonce) = nonce()
        post("/v1/auth/google", """{"idToken":"${googleToken(subject, appleNonce)}","nonce":"$appleNonce"}""").andExpect { status { isUnauthorized() } }
        // 두 번째 표기의 iss도 받는다
        googleSignIn(subject) { googleToken(subject, it, issuer = "accounts.google.com") }.andExpect { status { isAccepted() } }

        val nonce = googleNonce()
        val body = """{"idToken":"${googleToken(subject, nonce)}","nonce":"$nonce"}"""
        post("/v1/auth/google", body).andExpect { status { isAccepted() } }
        post("/v1/auth/google", body).andExpect { status { isUnauthorized() } }

        val unverified = "google-${UUID.randomUUID()}"
        val link = read(googleSignIn(unverified) { googleToken(unverified, it, emailVerified = false) }.andExpect { status { isAccepted() } })
        assertTrue(link["email"].isNull, link.toString())
        post("/v1/auth/google/register", """{"linkToken":"${link["linkToken"].asString()}"}""").andExpect { status { isCreated() } }
        assertTrue(jdbc.sql("select email is null from user_identities where subject = ?").param(unverified).query(Boolean::class.java).single())
    }

    private companion object {
        val KEY: RSAKey = RSAKeyGenerator(2048).keyID("test-apple").generate()
        val ENCODER = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(KEY)))
    }
}
