package io.wid.dearby.adaptor.security

import io.wid.dearby.application.TokenPair
import io.wid.dearby.application.TokenService
import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserRole
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@AutoConfigureMockMvc
class TokenFlowTest(
    @Autowired val mvc: MockMvc,
    @Autowired val jdbc: JdbcClient,
    @Autowired val tokens: TokenService,
    @Autowired val decoder: JwtDecoder,
) {
    private val json = JsonMapper.builder().build()

    private fun createUser(vararg roles: UserRole): User {
        val user = User(UUID.randomUUID(), roles.toList())
        jdbc.sql("insert into users (id) values (?)").param(user.id).update()
        roles.forEach {
            jdbc.sql("insert into user_roles (user_id, role) values (?, ?)").params(user.id, it.name).update()
        }
        return user
    }

    private fun refresh(token: String) = mvc.post("/v1/auth/refresh") {
        contentType = org.springframework.http.MediaType.APPLICATION_JSON
        content = """{"refreshToken":"$token"}"""
    }

    @Test
    fun `refresh token은 한 번만 쓸 수 있고 새 쌍을 받는다`() {
        val issued = tokens.issue(createUser(UserRole.USER))

        val body = refresh(issued.refreshToken).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val renewed = json.readValue(body, TokenPair::class.java)

        assertNotEquals(issued.refreshToken, renewed.refreshToken)
        assertEquals(60, renewed.expiresIn)
        refresh(issued.refreshToken).andExpect { status { isUnauthorized() } }
        refresh(renewed.refreshToken).andExpect { status { isOk() } }
    }

    @Test
    fun `만료되었거나 모르는 refresh token은 거부한다`() {
        val issued = tokens.issue(createUser(UserRole.USER))
        jdbc.sql("update refresh_tokens set expires_at = created_at").update()

        refresh(issued.refreshToken).andExpect { status { isUnauthorized() } }
        refresh(UUID.randomUUID().toString()).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `access token에 User id와 roles가 들어가고 권한으로 인증된다`() {
        val user = createUser(UserRole.ADMIN, UserRole.USER)
        val access = tokens.issue(user).accessToken

        val jwt = decoder.decode(access)
        assertEquals(user.id.toString(), jwt.subject)
        assertEquals(listOf("ADMIN", "USER"), jwt.getClaimAsStringList("roles")?.sorted())
        // 인증되면 없는 경로는 404, 토큰이 없으면 401
        mvc.get("/v1/none") { header("Authorization", "Bearer $access") }.andExpect { status { isNotFound() } }
        mvc.get("/v1/none").andExpect { status { isUnauthorized() } }
    }
}
