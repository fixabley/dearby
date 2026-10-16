package io.wid.dearby.auth.security

import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URLDecoder
import java.nio.file.Files
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 애플 서버 대신 MockRestServiceServer로 요청 형식과 client secret 서명을 확인한다
class RestAppleTokenClientTest {

    private val key = ECKeyGenerator(Curve.P_256).generate()
    private val keyFile = Files.createTempFile("apple", ".p8").also {
        val pem = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.toECPrivateKey().encoded)
        Files.writeString(it, "-----BEGIN PRIVATE KEY-----\n$pem\n-----END PRIVATE KEY-----\n")
    }
    private val properties = AppleProperties(
        clientIds = listOf("io.wid.dearby", "io.wid.dearby.web"),
        servicesId = "io.wid.dearby.web",
        callbackUrl = "https://api.example.test/v1/auth/apple/callback",
        teamId = "TEAM123456",
        keyId = "KEY1234567",
        privateKeyPath = keyFile.toString(),
    )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = RestAppleTokenClient(properties, builder)

    private fun form(body: String) = body.split('&').associate { part ->
        part.split('=', limit = 2).let { URLDecoder.decode(it[0], Charsets.UTF_8) to URLDecoder.decode(it[1], Charsets.UTF_8) }
    }

    @Test
    fun `client secret은 팀 ID·client_id·키 ID로 서명한 ES256 JWT다`() {
        val jwt = SignedJWT.parse(client.clientSecret("io.wid.dearby"))
        assertTrue(jwt.verify(ECDSAVerifier(key.toECPublicKey())))
        assertEquals("KEY1234567", jwt.header.keyID)
        assertEquals("TEAM123456", jwt.jwtClaimsSet.issuer)
        assertEquals("io.wid.dearby", jwt.jwtClaimsSet.subject)
        assertEquals(listOf("https://appleid.apple.com"), jwt.jwtClaimsSet.audience)
    }

    @Test
    fun `code를 refresh token으로 바꾸고, 웹 방식이면 redirect_uri를 함께 보낸다`() {
        var sent = emptyMap<String, String>()
        server.expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
            .andExpect { sent = form((it as MockClientHttpRequest).bodyAsString) }
            .andRespond(withSuccess("""{"access_token":"a","refresh_token":"r-1","id_token":"i"}""", MediaType.APPLICATION_JSON))

        assertEquals("r-1", client.refreshToken("code-1", "io.wid.dearby.web"))
        assertEquals("io.wid.dearby.web", sent["client_id"])
        assertEquals("authorization_code", sent["grant_type"])
        assertEquals("code-1", sent["code"])
        assertEquals("https://api.example.test/v1/auth/apple/callback", sent["redirect_uri"])
        server.verify()
    }

    @Test
    fun `폐기는 refresh token을 보내고, 애플이 거절하면 false`() {
        var sent = emptyMap<String, String>()
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
            .andExpect { sent = form((it as MockClientHttpRequest).bodyAsString) }
            .andRespond(withStatus(HttpStatus.OK))
        server.expect(requestTo("https://appleid.apple.com/auth/revoke")).andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertTrue(client.revoke("r-1", "io.wid.dearby"))
        assertEquals(mapOf("client_id" to "io.wid.dearby", "token" to "r-1", "token_type_hint" to "refresh_token"), sent - "client_secret")
        assertFalse(client.revoke("r-2", "io.wid.dearby"))
        server.verify()
    }

    @Test
    fun `키 설정이 없으면 애플에 요청하지 않는다`() {
        val unconfigured = RestAppleTokenClient(properties.copy(privateKeyPath = null), RestClient.builder())
        assertNull(unconfigured.refreshToken("code", "io.wid.dearby"))
        assertFalse(unconfigured.revoke("r", "io.wid.dearby"))
    }
}
