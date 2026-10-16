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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        jdbc.sql("insert into user_roles (user_id, role) values (?, 'USER')").param(id).update()
        jdbc.sql("insert into contacts (user_id, kind, label, value) values (?, 'EMAIL', '메일', 'a@example.com')").param(id).update()
        jdbc.sql("insert into profiles (user_id, name, created_at, updated_at) values (?, '이름', localtimestamp, localtimestamp)").param(id).update()
        val card = jdbc.sql("insert into business_cards (user_id, title) values (?, '명함') returning id").param(id).query(Long::class.java).single()
        jdbc.sql("insert into business_card_contacts (business_card_id, contact_id) values (?, 1)").param(card).update()
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

    @Test
    fun `계정을 지우면 사용자의 모든 행이 지워지고 애플 토큰을 폐기한다`() {
        val user = userWithData("rt-1")
        val other = userWithData(null)
        val issued = tokens.issue(user)

        mvc.delete("/v1/me") { header("Authorization", "Bearer ${issued.accessToken}") }.andExpect { status { isNoContent() } }

        assertEquals(listOf("io.wid.dearby:rt-1"), REVOKED)
        for ((table, column) in listOf("users" to "id", "user_roles" to "user_id", "contacts" to "user_id", "profiles" to "user_id",
            "business_cards" to "user_id", "refresh_tokens" to "user_id", "user_identities" to "user_id")) {
            assertEquals(0, count(table, column, user.id), table)
        }
        assertEquals(0, count("user_entities", "id", handle(user.id)))
        assertEquals(0, jdbc.sql("select count(*) from business_card_contacts c left join business_cards b on b.id = c.business_card_id where b.id is null").query(Int::class.java).single())
        assertEquals(1, count("user_entities", "id", handle(other.id)))
        assertEquals(1, count("users", "id", other.id))
        assertEquals(1, count("profiles", "user_id", other.id))
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
        mvc.delete("/v1/me") { header("Authorization", "Bearer ${tokens.issue(noToken).accessToken}") }.andExpect { status { isNoContent() } }
        assertEquals(0, count("user_identities", "user_id", noToken.id))
        assertEquals(listOf("io.wid.dearby:rejected"), REVOKED)

        mvc.delete("/v1/me").andExpect { status { isUnauthorized() } }
    }

    private companion object {
        val REVOKED = mutableListOf<String>()
    }
}
