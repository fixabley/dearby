package io.wid.dearby.adaptor.security

import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import io.wid.dearby.application.auth.PasskeyService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// webauthn4j 테스트 인증기로 실제 서명·검증을 거치는 가입 → 로그인 → refresh → logout 흐름
@SpringBootTest
@AutoConfigureMockMvc
class PasskeyFlowTest(
    @Autowired val mvc: MockMvc,
    @Autowired val jdbc: JdbcClient,
    @Autowired val decoder: JwtDecoder,
) {
    private val json = JsonMapper.builder().build()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    // 앱의 OS 패스키 API 역할. 같은 인스턴스가 만든 패스키를 기억한다
    private val device =
        ClientPlatform(Origin("http://localhost"), WebAuthnAuthenticatorAdaptor(NoneAttestationAuthenticator()))

    private fun post(path: String, body: String? = null) = mvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        body?.let { content = it }
    }

    private fun ResultActionsDsl.body(): JsonNode = json.readTree(andReturn().response.contentAsString)

    private fun registrationOptions(displayName: String? = null): JsonNode =
        post("/v1/auth/passkeys/registration/options", displayName?.let { """{"displayName":"$it"}""" })
            .andExpect { status { isOk() } }.body()

    // 서버가 준 options(JSON)로 패스키를 만들고 RegistrationResponseJSON을 돌려준다. iOS가 보내는 필드만 쓴다(transports 없음)
    private fun createCredential(options: JsonNode): String {
        val created = device.create(
            PublicKeyCredentialCreationOptions(
                PublicKeyCredentialRpEntity(options["rp"]["id"].asString(), options["rp"]["name"].asString()),
                PublicKeyCredentialUserEntity(
                    b64d.decode(options["user"]["id"].asString()),
                    options["user"]["name"].asString(),
                    options["user"]["displayName"].asString(),
                ),
                DefaultChallenge(b64d.decode(options["challenge"].asString())),
                listOf(
                    PublicKeyCredentialParameters(
                        PublicKeyCredentialType.PUBLIC_KEY,
                        COSEAlgorithmIdentifier.ES256
                    )
                ),
                options["timeout"].asLong(),
                emptyList(),
                AuthenticatorSelectionCriteria(
                    null,
                    ResidentKeyRequirement.create(options["authenticatorSelection"]["residentKey"].asString()),
                    UserVerificationRequirement.create(options["authenticatorSelection"]["userVerification"].asString()),
                ),
                null,
                AttestationConveyancePreference.create(options["attestation"].asString()),
                null,
            )
        )
        val id = b64.encodeToString(created.rawId)
        val response = created.response!!
        return """{"id":"$id","rawId":"$id","type":"public-key","authenticatorAttachment":"platform","clientExtensionResults":{},
            "response":{"clientDataJSON":"${b64.encodeToString(response.clientDataJSON)}",
            "attestationObject":"${b64.encodeToString(response.attestationObject)}"}}"""
    }

    // 서버가 준 options(JSON)로 서명해 AuthenticationResponseJSON을 돌려준다
    private fun getAssertion(options: JsonNode): String {
        val assertion = device.get(
            PublicKeyCredentialRequestOptions(
                DefaultChallenge(b64d.decode(options["challenge"].asString())),
                options["timeout"].asLong(),
                options["rpId"].asString(),
                emptyList(),
                UserVerificationRequirement.create(options["userVerification"].asString()),
                null,
            )
        )
        val id = b64.encodeToString(assertion.rawId)
        val response = assertion.response!!
        return """{"id":"$id","rawId":"$id","type":"public-key","authenticatorAttachment":"platform","clientExtensionResults":{},
            "response":{"clientDataJSON":"${b64.encodeToString(response.clientDataJSON)}",
            "authenticatorData":"${b64.encodeToString(response.authenticatorData)}",
            "signature":"${b64.encodeToString(response.signature)}",
            "userHandle":"${b64.encodeToString(response.userHandle)}"}}"""
    }

    private fun complete(path: String, challengeId: String, credential: String) =
        post(path, """{"challengeId":"$challengeId","credential":$credential}""")

    private fun register(displayName: String? = null): JsonNode {
        val issued = registrationOptions(displayName)
        return complete(
            "/v1/auth/passkeys/registration",
            issued["challengeId"].asString(),
            createCredential(issued["options"]),
        ).andExpect { status { isCreated() } }.body()
    }

    private fun authenticationOptions(): JsonNode =
        post("/v1/auth/passkeys/authentication/options").andExpect { status { isOk() } }.body()

    private fun authenticate(): ResultActionsDsl {
        val issued = authenticationOptions()
        return complete(
            "/v1/auth/passkeys/authentication",
            issued["challengeId"].asString(),
            getAssertion(issued["options"]),
        )
    }

    private fun subject(tokens: JsonNode) = decoder.decode(tokens["accessToken"].asString()).subject

    private fun count(table: String) = jdbc.sql("select count(*) from $table").query(Long::class.java).single()

    @Test
    fun `가입하면 계정이 생기고 그 패스키로 로그인한 뒤 refresh와 logout을 한다`() {
        val signedUp = register("민준")
        assertEquals(setOf("accessToken", "refreshToken"), signedUp.propertyNames().toSet())
        val userId = subject(signedUp)
        assertEquals(
            listOf("USER"),
            jdbc.sql("select role from user_roles where user_id = ?").param(UUID.fromString(userId))
                .query(String::class.java).list(),
        )
        assertEquals(
            "민준",
            jdbc.sql("select display_name from user_entities where name = ?").param(userId)
                .query(String::class.java).single(),
        )

        val loggedIn = authenticate().andExpect { status { isOk() } }.body()
        assertEquals(userId, subject(loggedIn))
        val access = loggedIn["accessToken"].asString()
        // 발급한 access token으로 인증된다(없는 경로라 404)
        mvc.get("/v1/none") { header("Authorization", "Bearer $access") }.andExpect { status { isNotFound() } }

        // 만료된 access token이 붙어 와도 인증 경로는 막지 않는다
        val refreshed = mvc.post("/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer invalid")
            content = """{"refreshToken":"${loggedIn["refreshToken"].asString()}"}"""
        }.andExpect { status { isOk() } }.body()
        assertEquals(setOf("accessToken", "refreshToken"), refreshed.propertyNames().toSet())
        assertEquals(userId, subject(refreshed))
        assertNotEquals(loggedIn["refreshToken"].asString(), refreshed["refreshToken"].asString())

        val logout = """{"refreshToken":"${refreshed["refreshToken"].asString()}"}"""
        post("/v1/auth/logout", logout).andExpect { status { isNoContent() } }
        post("/v1/auth/logout", logout).andExpect { status { isNoContent() } }
        post("/v1/auth/refresh", logout).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `가입 options는 서버가 정한 user와 기본 이름을 준다`() {
        val options = registrationOptions()["options"]
        assertEquals(PasskeyService.DEFAULT_DISPLAY_NAME, options["user"]["displayName"].asString())
        UUID.fromString(options["user"]["name"].asString())
        assertEquals("localhost", options["rp"]["id"].asString())
        assertEquals("required", options["authenticatorSelection"]["residentKey"].asString())

        val request = authenticationOptions()["options"]
        assertEquals("localhost", request["rpId"].asString())
        assertEquals(0, request["allowCredentials"]?.size() ?: 0)
    }

    @Test
    fun `challenge는 한 번만 쓸 수 있다`() {
        val issued = registrationOptions()
        val challengeId = issued["challengeId"].asString()
        val credential = createCredential(issued["options"])
        complete("/v1/auth/passkeys/registration", challengeId, credential).andExpect { status { isCreated() } }
        complete("/v1/auth/passkeys/registration", challengeId, credential).andExpect { status { isUnauthorized() } }

        val login = authenticationOptions()
        val assertion = getAssertion(login["options"])
        complete("/v1/auth/passkeys/authentication", login["challengeId"].asString(), assertion)
            .andExpect { status { isOk() } }
        complete("/v1/auth/passkeys/authentication", login["challengeId"].asString(), assertion)
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `만료된 challenge는 거부하고 계정을 만들지 않는다`() {
        val issued = registrationOptions()
        val credential = createCredential(issued["options"])
        jdbc.sql("update webauthn_challenges set expires_at = ?").param(java.time.OffsetDateTime.now().minusSeconds(1))
            .update()
        val users = count("users")

        complete("/v1/auth/passkeys/registration", issued["challengeId"].asString(), credential)
            .andExpect { status { isUnauthorized() } }
        assertEquals(users, count("users"))
    }

    @Test
    fun `다른 challenge로 서명한 응답은 거부하고 계정을 만들지 않는다`() {
        val signed = registrationOptions()
        val other = registrationOptions()
        val users = count("users")

        complete("/v1/auth/passkeys/registration", other["challengeId"].asString(), createCredential(signed["options"]))
            .andExpect { status { isUnauthorized() } }
        assertEquals(users, count("users"))
        assertEquals(
            0, jdbc.sql("select count(*) from user_entities where name = ?")
                .param(other["options"]["user"]["name"].asString()).query(Long::class.java).single()
        )
    }

    @Test
    fun `모르는 패스키로 로그인하면 401`() {
        val userId = subject(register())
        jdbc.sql("delete from user_credentials where user_entity_user_id = (select id from user_entities where name = ?)")
            .param(userId).update()

        authenticate().andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `형식이 틀린 요청은 400`() {
        val issued = authenticationOptions()
        complete("/v1/auth/passkeys/authentication", issued["challengeId"].asString(), """{"id":1}""")
            .andExpect { status { isBadRequest() } }
        post("/v1/auth/passkeys/authentication", """{"credential":{}}""").andExpect { status { isBadRequest() } }
        post("/v1/auth/passkeys/registration/options", """{"displayName":"${"가".repeat(65)}"}""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `Android 앱 origin 형식을 허용 목록 값으로 쓸 수 있다`() {
        assertEquals("android:apk-key-hash:AAAA", Origin("android:apk-key-hash:AAAA").toString())
    }
}
