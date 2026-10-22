package io.wid.dearby.auth.security

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
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionFlowTest(
    @Autowired val mvc: MockMvc,
    @Autowired val jdbc: JdbcClient,
    @Autowired val tokens: TokenService,
) {
    // 애플 토큰 엔드포인트 대신 폐기 요청을 기록한다
    @TestConfiguration
    class FakeAppleTokens {
        @Bean
        @Primary
        fun fakeAppleTokenClient() = object : AppleTokenClient {
            override fun refreshToken(authorizationCode: String, clientId: String) = "rt-$authorizationCode"
            override fun revoke(refreshToken: String, clientId: String): Boolean {
                REVOKED += "$clientId:$refreshToken"
                return refreshToken != "rejected"
            }
        }
    }

    @BeforeTest
    fun clear() = REVOKED.clear()

    private fun userWithData(appleToken: String?): User {
        val user = User(UUID.randomUUID(), listOf(UserRole.USER))
        val id = user.id
        jdbc.sql("insert into users (id) values (?)").param(id).update()
        jdbc.sql("insert into user_consents (user_id, privacy_version, terms_version, consented_at) values (?, '2026-10-22', '2026-10-22', now())").param(id).update()
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(id).update()
        jdbc.sql("insert into contacts (id, user_id, kind, label, value, position) values (?, ?, 'EMAIL', '메일', 'a@example.com', 0)").params(UUID.randomUUID(), id).update()
        jdbc.sql("insert into profiles (user_id, name, introduction, version, created_at, updated_at) values (?, '이름', '', 1, now(), now())").param(id).update()
        jdbc.sql("insert into profile_jobs (id, user_id, job_name, position, approval_status, created_at, updated_at) values (?, ?, '직접 적은 직무', 0, 'PENDING', now(), now())").params(UUID.randomUUID(), id).update()
        jdbc.sql("insert into profile_versions (user_id, version, snapshot, created_at) values (?, 1, '{}', now())").param(id).update()
        val history = UUID.randomUUID()
        jdbc.sql("insert into profile_histories (id, user_id, kind, organization_name, role_name, start_date, description, position, approval_status, verification_status, created_at, updated_at) values (?, ?, 'ORGANIZATION', '조직', '역할', '2026-01-01', '', 0, 'PENDING', 'UNVERIFIED', now(), now())").params(history, id).update()
        jdbc.sql("insert into history_evidence (id, history_id, user_id, file_name, content_type, size, data, uploaded_at) values (?, ?, ?, 'a.png', 'image/png', 1, '\\x00', now())").params(UUID.randomUUID(), history, id).update()
        val card = UUID.randomUUID()
        jdbc.sql("insert into business_cards (id, user_id, name, description, template, introduction, profile_name, job, contacts, histories, created_at, updated_at) values (?, ?, '명함', '', '', '', '이름', '', '[]', '[]', now(), now())").params(card, id).update()
        // 다른 사람이 내 명함을 받아 둔 지갑 행
        val stranger = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(stranger).update()
        val receipt = UUID.randomUUID()
        jdbc.sql("insert into receipts (id, recipient_id, card_id, context, received_at) values (?, ?, ?, '{}', now())").params(receipt, stranger, card).update()
        // 내 활동과 일정 상태(native-v1.md "내 활동 서버 저장"). 일정 상태는 논리키라 직접 지운다
        jdbc.sql("insert into user_activities (id, user_id, activity_id, created_at, updated_at) values (?, ?, -1, now(), now())").params(id, id).update()
        jdbc.sql("insert into user_activity_schedule_statuses (user_activity_id, schedule_id, default_kind, updated_at) values (?, -1, 'PASSED', now())").param(id).update()
        // 패스키: user handle = UUID 16바이트(base64url)
        jdbc.sql("insert into user_entities (id, name, display_name) values (?, 'Dearby', 'Dearby')").param(handle(id)).update()
        appleToken?.let {
            jdbc.sql("insert into user_identities (provider, subject, user_id, client_id, refresh_token, created_at) values ('APPLE', ?, ?, 'io.wid.dearby', ?, now())")
                .params("apple-$id", id, it).update()
        }
        return user
    }

    private fun handle(id: UUID) =
        Bytes(ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()).toBase64UrlString()

    private fun count(table: String, column: String, value: Any) =
        jdbc.sql("select count(*) from $table where $column = ?").param(value).query(Int::class.java).single()

    private val json = JsonMapper.builder().build()

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun get(path: String, access: String) = mvc.get(path) { header("Authorization", "Bearer $access") }

    private fun unlink(provider: String, access: String) = mvc.delete("/v1/me/identities/$provider") { header("Authorization", "Bearer $access") }

    // 설정 화면(native-v1.md "설정"): 가입일·로그인 수단 요약, 연결된 로그인 목록, 연결 해제. 마지막 수단은 해제할 수 없다
    @Test
    fun `연결된 로그인을 보고 해제하되 마지막 수단은 남기며, 애플 해제는 토큰을 폐기한다`() {
        val user = userWithData("rt-unlink")
        val access = tokens.issue(user).accessToken

        // 애플만 연결된 계정(패스키 없음): 해제하면 로그인할 길이 없으므로 409
        val me = read(get("/v1/me", access).andExpect { status { isOk() } })
        assertEquals(user.id.toString(), me["id"].asString())
        assertTrue(me["createdAt"].isString, me.toString())
        assertEquals(0, me["signInMethods"]["passkeys"].asInt())
        assertEquals(true, me["signInMethods"]["apple"].asBoolean())
        assertEquals(false, me["signInMethods"]["google"].asBoolean())
        val last = read(unlink("apple", access).andExpect { status { isConflict() } })
        assertEquals("LAST_SIGN_IN_METHOD", last["code"].asString())
        assertEquals(emptyList<String>(), REVOKED)
        // 연결되지 않은 provider와 모르는 provider는 404
        unlink("google", access).andExpect { status { isNotFound() } }
        unlink("kakao", access).andExpect { status { isNotFound() } }

        jdbc.sql("insert into user_identities (provider, subject, user_id, email, display_name, created_at) values ('GOOGLE', ?, ?, 'g@gmail.com', '조민준', now())")
            .params("google-${user.id}", user.id).update()
        val items = read(get("/v1/me/identities", access).andExpect { status { isOk() } })["items"]
        assertEquals(listOf("apple", "google"), items.values().map { it["provider"].asString() })
        assertEquals("g@gmail.com", items[1]["email"].asString())
        assertEquals("조민준", items[1]["displayName"].asString())
        assertTrue(items[0]["linkedAt"].isString, items.toString())
        assertEquals(true, read(get("/v1/me", access))["signInMethods"]["google"].asBoolean())

        // 구글이 있으니 애플을 해제할 수 있고, 저장한 애플 토큰은 폐기한다(계정 삭제와 같은 경로)
        unlink("apple", access).andExpect { status { isNoContent() } }
        assertEquals(listOf("io.wid.dearby:rt-unlink"), REVOKED)
        assertEquals(listOf("google"), read(get("/v1/me/identities", access))["items"].values().map { it["provider"].asString() })
        unlink("apple", access).andExpect { status { isNotFound() } }
        // 이제 구글이 마지막 수단
        unlink("google", access).andExpect { status { isConflict() } }
        assertEquals(1, count("user_identities", "user_id", user.id))

        mvc.get("/v1/me") {}.andExpect { status { isUnauthorized() } }
        mvc.get("/v1/me/identities") {}.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `계정을 지우면 사용자의 모든 행이 지워지고 애플 토큰을 폐기한다`() {
        val user = userWithData("rt-1")
        val other = userWithData(null)
        val issued = tokens.issue(user)

        mvc.delete("/v1/me") { header("Authorization", "Bearer ${issued.accessToken}") }.andExpect { status { isNoContent() } }

        assertEquals(listOf("io.wid.dearby:rt-1"), REVOKED)
        for ((table, column) in listOf("users" to "id", "user_roles" to "user_id", "contacts" to "user_id", "profiles" to "user_id",
            "profile_versions" to "user_id", "profile_histories" to "user_id", "history_evidence" to "user_id", "profile_jobs" to "user_id",
            "business_cards" to "user_id", "refresh_tokens" to "user_id", "user_identities" to "user_id")) {
            assertEquals(0, count(table, column, user.id), table)
        }
        assertEquals(0, count("user_entities", "id", handle(user.id)))
        assertEquals(0, count("user_consents", "user_id", user.id))
        assertEquals(0, count("user_activities", "user_id", user.id))
        assertEquals(0, count("user_activity_schedule_statuses", "user_activity_id", user.id))
        assertEquals(1, count("user_activity_schedule_statuses", "user_activity_id", other.id))
        // 내 명함을 받아 둔 다른 사람의 지갑 행도 함께 지워진다
        assertEquals(0, jdbc.sql("select count(*) from receipts r left join business_cards b on b.id = r.card_id where b.id is null").query(Int::class.java).single())
        assertEquals(1, count("user_entities", "id", handle(other.id)))
        assertEquals(1, count("users", "id", other.id))
        assertEquals(1, count("profiles", "user_id", other.id))
        assertEquals(1, count("user_consents", "user_id", other.id))
        // 지운 계정의 refresh token은 더 쓸 수 없다
        mvc.post("/v1/auth/refresh") { contentType = MediaType.APPLICATION_JSON; content = """{"refreshToken":"${issued.refreshToken}"}""" }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `애플 토큰 폐기가 실패하거나 토큰이 없어도 삭제는 진행하고, 로그인하지 않으면 401`() {
        val rejected = userWithData("rejected")
        mvc.delete("/v1/me") { header("Authorization", "Bearer ${tokens.issue(rejected).accessToken}") }.andExpect { status { isNoContent() } }
        assertEquals(0, count("users", "id", rejected.id))

        val noToken = userWithData(null)
        jdbc.sql("insert into user_identities (provider, subject, user_id, created_at) values ('APPLE', ?, ?, now())").params("apple-x-${noToken.id}", noToken.id).update()
        // 구글 계정(refresh token 없음)도 함께 지워진다. 폐기할 토큰이 없으니 REVOKED에는 남지 않는다
        jdbc.sql("insert into user_identities (provider, subject, user_id, created_at) values ('GOOGLE', ?, ?, now())").params("google-${noToken.id}", noToken.id).update()
        mvc.delete("/v1/me") { header("Authorization", "Bearer ${tokens.issue(noToken).accessToken}") }.andExpect { status { isNoContent() } }
        assertEquals(0, count("user_identities", "user_id", noToken.id))
        assertEquals(listOf("io.wid.dearby:rejected"), REVOKED)

        mvc.delete("/v1/me").andExpect { status { isUnauthorized() } }
    }

    private companion object {
        val REVOKED = mutableListOf<String>()
    }
}
