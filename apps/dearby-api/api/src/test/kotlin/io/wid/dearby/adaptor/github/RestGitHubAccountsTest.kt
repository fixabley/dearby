package io.wid.dearby.adaptor.github

import io.wid.dearby.application.profile.LinkPlatform
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// GitHub 서버 대신 MockRestServiceServer로 토큰 교환·사용자 조회의 요청 형식을 확인한다
class RestGitHubAccountsTest {

    private val properties = GitHubProperties(
        clientId = "client-id",
        clientSecret = "client-secret",
        callbackUrl = "https://api.example.test/v1/me/contacts/github/callback",
        webRedirectUri = "https://web.example.test/profile",
    )
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val accounts = RestGitHubAccounts(properties, builder)

    @Test
    fun `설정이 비면 쓸 수 없고, 인가 주소는 scope 없이 client_id·redirect_uri·state만 싣는다`() {
        assertTrue(accounts.isConfigured())
        assertFalse(RestGitHubAccounts(properties.copy(clientSecret = ""), RestClient.builder()).isConfigured())
        val state = UUID.randomUUID()
        // 애플 인가 주소와 같은 인코딩(쿼리 값의 :·/는 그대로)
        assertEquals(
            "https://github.com/login/oauth/authorize?client_id=client-id&redirect_uri=https://api.example.test/v1/me/contacts/github/callback&state=$state",
            accounts.authorizeUrl(state),
        )
        assertEquals("dearby://contacts/github?code=abc", accounts.redirect(LinkPlatform.ANDROID, "code", "abc").toString())
        assertEquals("https://web.example.test/profile?error=cancelled", accounts.redirect(LinkPlatform.WEB, "error", "cancelled").toString())
    }

    @Test
    fun `code를 토큰으로 바꾸고 Bearer로 user의 login을 읽는다`() {
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.USER_AGENT, "Dearby"))
            .andExpect(content().formData(
                org.springframework.util.LinkedMultiValueMap(mapOf(
                    "client_id" to listOf("client-id"), "client_secret" to listOf("client-secret"), "code" to listOf("gh-code"),
                    "redirect_uri" to listOf("https://api.example.test/v1/me/contacts/github/callback"),
                )),
            ))
            .andRespond(withSuccess("""{"access_token":"gho_token","token_type":"bearer","scope":""}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.github.com/user"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_token"))
            .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
            .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
            .andRespond(withSuccess("""{"login":"octocat","id":1,"html_url":"https://github.com/octocat"}""", MediaType.APPLICATION_JSON))
        assertEquals("octocat", accounts.login("gh-code"))
        server.verify()
    }

    @Test
    fun `GitHub이 200에 error를 주거나 서버 오류면 null`() {
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andRespond(withSuccess("""{"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}""", MediaType.APPLICATION_JSON))
        assertNull(accounts.login("bad"))
        server.verify()
        server.reset()
        server.expect(requestTo("https://github.com/login/oauth/access_token")).andRespond(withServerError())
        assertNull(accounts.login("gh-code"))
    }
}
