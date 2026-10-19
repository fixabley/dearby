package io.wid.dearby.auth.security

import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import io.wid.dearby.application.auth.PasskeyService
import io.wid.dearby.application.auth.TokenService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// webauthn4j 테스트 인증기로 실제 서명·검증을 거치는 가입 → 로그인 → refresh → logout 흐름
@SpringBootTest
@AutoConfigureMockMvc
class PasskeyFlowTest(
    @Autowired val mvc: MockMvc,
    @Autowired val jdbc: JdbcClient,
    @Autowired val decoder: JwtDecoder,
    @Autowired val tokens: TokenService,
) {
    private val json = JsonMapper.builder().build()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    // 앱의 OS 패스키 API 역할. 같은 인스턴스가 만든 패스키를 기억한다
    private val device =
        ClientPlatform(Origin("http://localhost"), WebAuthnAuthenticatorAdaptor(NoneAttestationAuthenticator()))

    private fun post(path: String, body: String? = null, bearer: String? = null) = mvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        body?.let { content = it }
        bearer?.let { header("Authorization", "Bearer $it") }
    }

    private fun ResultActionsDsl.body(): JsonNode = json.readTree(andReturn().response.contentAsString)

    private fun registrationOptions(displayName: String? = null, bearer: String? = null): JsonNode =
        post("/v1/auth/passkeys/registration/options", displayName?.let { """{"displayName":"$it"}""" }, bearer)
            .andExpect { status { isOk() } }.body()

    // 서버가 준 options(JSON)로 패스키를 만들고 RegistrationResponseJSON을 돌려준다. iOS가 보내는 필드만 쓴다(transports 없음)
    private fun createCredential(options: JsonNode, on: ClientPlatform = device): String {
        val created = on.create(
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
    private fun getAssertion(options: JsonNode, on: ClientPlatform = device): String {
        val assertion = on.get(
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

    // 사용자 UUID → user handle(user_entities.id, base64url)
    private fun handle(userId: String?): String {
        val uuid = UUID.fromString(userId)
        return b64.encodeToString(java.nio.ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array())
    }

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

    private fun authenticate(on: ClientPlatform = device): ResultActionsDsl {
        val issued = authenticationOptions()
        return complete(
            "/v1/auth/passkeys/authentication",
            issued["challengeId"].asString(),
            getAssertion(issued["options"], on),
        )
    }

    private fun get(path: String, access: String) = mvc.get(path) { header("Authorization", "Bearer $access") }

    private fun passkeys(access: String): JsonNode = get("/v1/me/passkeys", access).andExpect { status { isOk() } }.body()["items"]

    private fun rename(id: String, label: String, access: String) = mvc.patch("/v1/me/passkeys/$id") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"label":"$label"}"""
        header("Authorization", "Bearer $access")
    }

    private fun remove(id: String, access: String) = mvc.delete("/v1/me/passkeys/$id") { header("Authorization", "Bearer $access") }

    private fun newDevice() = ClientPlatform(Origin("http://localhost"), WebAuthnAuthenticatorAdaptor(NoneAttestationAuthenticator()))

    private fun subject(tokens: JsonNode) = decoder.decode(tokens["accessToken"].asString()).subject

    private fun count(table: String) = jdbc.sql("select count(*) from $table").query(Long::class.java).single()

    @Test
    fun `가입하면 계정이 생기고 그 패스키로 로그인한 뒤 refresh와 logout을 한다`() {
        val signedUp = register("민준")
        assertEquals(setOf("accessToken", "refreshToken", "user"), signedUp.propertyNames().toSet())
        val userId = subject(signedUp)
        // 응답의 user는 access token의 sub·roles와 같다
        assertEquals(userId, signedUp["user"]["id"].asString())
        assertEquals(listOf("USER"), signedUp["user"]["roles"].values().map { it.asString() })
        assertEquals(
            listOf("USER"),
            jdbc.sql("select role from user_roles where user_id = ?").param(UUID.fromString(userId))
                .query(String::class.java).list(),
        )
        assertEquals(
            "민준",
            jdbc.sql("select display_name from user_entities where id = ?").param(handle(userId))
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
        assertEquals(setOf("accessToken", "refreshToken", "user"), refreshed.propertyNames().toSet())
        assertEquals(userId, subject(refreshed))
        assertEquals(userId, refreshed["user"]["id"].asString())
        assertNotEquals(loggedIn["refreshToken"].asString(), refreshed["refreshToken"].asString())

        val logout = """{"refreshToken":"${refreshed["refreshToken"].asString()}"}"""
        post("/v1/auth/logout", logout).andExpect { status { isNoContent() } }
        post("/v1/auth/logout", logout).andExpect { status { isNoContent() } }
        post("/v1/auth/refresh", logout).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `가입 options는 서버가 정한 user handle과 사람이 읽는 이름을 준다`() {
        val options = registrationOptions()["options"]
        // OS 패스키 목록에 보이는 계정 이름은 기본 "Dearby"(2026-10-15), user handle은 사용자 UUID 16바이트
        assertEquals("Dearby", options["user"]["name"].asString())
        assertEquals("Dearby", options["user"]["displayName"].asString())
        assertEquals(16, b64d.decode(options["user"]["id"].asString()).size)
        assertEquals("민준", registrationOptions("민준")["options"]["user"]["name"].asString())
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
            0, jdbc.sql("select count(*) from user_entities where id = ?")
                .param(other["options"]["user"]["id"].asString()).query(Long::class.java).single()
        )
    }

    @Test
    fun `모르는 패스키로 로그인하면 401`() {
        val userId = subject(register())
        jdbc.sql("delete from user_credentials where user_entity_user_id = ?").param(handle(userId)).update()

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

    // 설정 화면 "패스키"(native-v1.md "설정"): 목록·이름 바꾸기·삭제, 로그인 상태에서 패스키 추가. 마지막 로그인 수단은 지울 수 없다
    @Test
    fun `패스키 목록을 보고 이름을 바꾸며, 로그인한 채로 더한 뒤에는 하나를 지울 수 있지만 마지막 것은 지울 수 없다`() {
        val signedUp = register("민준")
        val access = signedUp["accessToken"].asString()
        val userId = subject(signedUp)

        val first = passkeys(access)
        assertEquals(1, first.size())
        val firstId = first[0]["id"].asString()
        assertEquals("민준", first[0]["label"].asString())
        assertEquals(setOf("id", "label", "createdAt", "lastUsedAt", "backedUp", "transports"), first[0].propertyNames().toSet())
        assertTrue(first[0]["createdAt"].isString && first[0]["lastUsedAt"].isString && first[0]["backedUp"].isBoolean, first.toString())
        // iOS는 transports를 보내지 않으므로 빈 배열(빈 문자열이 항목으로 섞이지 않는다)
        assertEquals(0, first[0]["transports"].size())
        val me = get("/v1/me", access).andExpect { status { isOk() } }.body()
        assertEquals(userId, me["id"].asString())
        assertTrue(me["createdAt"].isString, me.toString())
        assertEquals(1, me["signInMethods"]["passkeys"].asInt())
        assertEquals(false, me["signInMethods"]["apple"].asBoolean())

        val renamed = rename(firstId, "  아이폰  ", access).andExpect { status { isOk() } }.body()
        assertEquals("아이폰", renamed["label"].asString())
        assertEquals("아이폰", passkeys(access)[0]["label"].asString())
        rename(firstId, "", access).andExpect { status { isBadRequest() } }
        rename(firstId, "가".repeat(61), access).andExpect { status { isBadRequest() } }
        // 유일한 로그인 수단은 지울 수 없다
        val conflict = remove(firstId, access).andExpect { status { isConflict() } }.body()
        assertEquals("LAST_SIGN_IN_METHOD", conflict["code"].asString())

        // 로그인한 채로 가입 options를 부르면 같은 계정(user handle)에 더한다. 있는 패스키는 excludeCredentials로 알린다
        val issued = registrationOptions("맥북", access)
        assertEquals(handle(userId), issued["options"]["user"]["id"].asString())
        // OS에 보이는 계정 이름은 기존 user entity 그대로
        assertEquals("민준", issued["options"]["user"]["name"].asString())
        assertEquals(listOf(firstId), issued["options"]["excludeCredentials"].values().map { it["id"].asString() })
        val added = complete("/v1/auth/passkeys/registration", issued["challengeId"].asString(), createCredential(issued["options"], newDevice()))
            .andExpect { status { isCreated() } }.body()
        assertEquals(userId, subject(added))
        // 계정 이름은 그대로(user entity를 덮어쓰지 않는다)
        assertEquals("민준", jdbc.sql("select display_name from user_entities where id = ?").param(handle(userId)).query(String::class.java).single())
        assertEquals(listOf("아이폰", "맥북"), passkeys(access).values().map { it["label"].asString() })
        assertEquals(2, get("/v1/me", access).body()["signInMethods"]["passkeys"].asInt())

        remove(firstId, access).andExpect { status { isNoContent() } }
        remove(firstId, access).andExpect { status { isNotFound() } }
        val remaining = passkeys(access)
        assertEquals(1, remaining.size())
        assertNotEquals(firstId, remaining[0]["id"].asString())
        remove(remaining[0]["id"].asString(), access).andExpect { status { isConflict() } }

        // 남의 패스키는 없는 것과 같다(404)
        val stranger = register()["accessToken"].asString()
        rename(remaining[0]["id"].asString(), "훔친 이름", stranger).andExpect { status { isNotFound() } }
        remove(remaining[0]["id"].asString(), stranger).andExpect { status { isNotFound() } }
        assertEquals(1, passkeys(access).size())

        // 가입 options에 무효한 토큰이 붙으면 401(다른 인증 경로는 무시한다)
        post("/v1/auth/passkeys/registration/options", null, "invalid").andExpect { status { isUnauthorized() } }
        mvc.get("/v1/me/passkeys") {}.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `2026-10-15 이전 가입분(무작위 user handle)에 패스키를 더하면 그 user entity에 붙고 그 계정으로 로그인된다`() {
        val userId = UUID.randomUUID()
        val legacyHandle = b64.encodeToString(ByteArray(32) { it.toByte() })
        jdbc.sql("insert into users (id) values (?)").param(userId).update()
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(userId).update()
        jdbc.sql("insert into user_entities (id, name, display_name) values (?, ?, '옛 이름')").params(legacyHandle, userId.toString()).update()
        val access = tokens.issue(io.wid.dearby.domain.user.User(userId, listOf(io.wid.dearby.domain.user.UserRole.USER))).accessToken

        val issued = registrationOptions(bearer = access)
        assertEquals(legacyHandle, issued["options"]["user"]["id"].asString())
        assertEquals(userId.toString(), issued["options"]["user"]["name"].asString())
        assertEquals(0, issued["options"]["excludeCredentials"].size())
        complete("/v1/auth/passkeys/registration", issued["challengeId"].asString(), createCredential(issued["options"]))
            .andExpect { status { isCreated() } }
        assertEquals("옛 이름", jdbc.sql("select display_name from user_entities where id = ?").param(legacyHandle).query(String::class.java).single())
        assertEquals(userId.toString(), subject(authenticate().andExpect { status { isOk() } }.body()))
        assertEquals(1, passkeys(access).size())
        assertEquals("Dearby", passkeys(access)[0]["label"].asString())
    }

    @Test
    fun `애플·구글로만 쓰던 계정(user entity 없음)이 첫 패스키를 더하면 새 user entity가 생기고 그 계정으로 로그인된다`() {
        val userId = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(userId).update()
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(userId).update()
        jdbc.sql("insert into user_identities (provider, subject, user_id, created_at) values ('APPLE', ?, ?, now())").params("apple-$userId", userId).update()
        val access = tokens.issue(io.wid.dearby.domain.user.User(userId, listOf(io.wid.dearby.domain.user.UserRole.USER))).accessToken
        assertEquals(0, get("/v1/me", access).andExpect { status { isOk() } }.body()["signInMethods"]["passkeys"].asInt())

        val issued = registrationOptions("민준", access)
        assertEquals(handle(userId.toString()), issued["options"]["user"]["id"].asString())
        assertEquals(0, issued["options"]["excludeCredentials"].size())
        val added = complete("/v1/auth/passkeys/registration", issued["challengeId"].asString(), createCredential(issued["options"]))
            .andExpect { status { isCreated() } }.body()
        assertEquals(userId.toString(), subject(added))
        assertEquals("민준", jdbc.sql("select display_name from user_entities where id = ?").param(handle(userId.toString())).query(String::class.java).single())
        assertEquals(userId.toString(), subject(authenticate().andExpect { status { isOk() } }.body()))
        val me = get("/v1/me", access).body()["signInMethods"]
        assertEquals(1, me["passkeys"].asInt())
        assertEquals(true, me["apple"].asBoolean())
        // 이제 패스키가 있으니 애플을 해제할 수 있다
        mvc.delete("/v1/me/identities/apple") { header("Authorization", "Bearer $access") }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `Android 앱 origin 형식을 허용 목록 값으로 쓸 수 있다`() {
        assertEquals("android:apk-key-hash:AAAA", Origin("android:apk-key-hash:AAAA").toString())
    }
}
