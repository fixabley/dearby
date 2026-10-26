package io.wid.dearby.adaptor.web

import io.wid.dearby.adaptor.github.GitHubProperties
import io.wid.dearby.adaptor.github.RestGitHubAccounts
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// native-v1.md "연락처 인증": GitHub 로그인 연결(start → 콜백 → code → complete). GitHub 서버는 가짜 login 조회로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContactVerificationFlowTest.FakeGitHub::class)
class ContactVerificationFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    @TestConfiguration
    class FakeGitHub {
        // 인가 주소·돌려보내기 주소는 실제 구현을 쓰고 GitHub HTTP(login 조회)만 code → login 표로 바꾼다
        @Bean @Primary
        fun gitHubAccounts(properties: GitHubProperties) = object : RestGitHubAccounts(properties, RestClient.builder()) {
            override fun login(code: String): String? = LOGINS[code]
        }
    }

    private val json = JsonMapper.builder().build()

    private fun user(): UUID = UUID.randomUUID().also {
        jdbc.sql("insert into users (id) values (?)").param(it).update()
        mvc.post("/v1/me/consents") { asUser(it); contentType = MediaType.APPLICATION_JSON; content = """{"privacyVersion":"2026-10-26","termsVersion":"2026-10-22"}""" }
            .andExpect { status { isNoContent() } }
    }

    private fun MockHttpServletRequestDsl.asUser(id: UUID) {
        with(jwt().jwt { it.subject(id.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun post(user: UUID?, path: String, body: String) = mvc.post(path) {
        user?.let { asUser(it) }; contentType = MediaType.APPLICATION_JSON; content = body
    }

    // phone·email 필수 + 선택한 추가 연락처로 프로필을 저장한다
    private fun saveProfile(user: UUID, extra: String = "", email: String = "me@example.com", emailId: UUID = UUID.randomUUID(), label: String = ""): JsonNode = read(
        mvc.put("/v1/profile") {
            asUser(user); contentType = MediaType.APPLICATION_JSON
            content = """{"name":"이름","contacts":[{"id":"${UUID.randomUUID()}","kind":"phone","label":"","value":"010-1234-5678"},{"id":"$emailId","kind":"email","label":"$label","value":"$email"}$extra]}"""
        }.andExpect { status { isOk() } },
    )

    private fun contact(profile: JsonNode, kind: String): JsonNode? = profile["contacts"].values().firstOrNull { it["kind"].asString() == kind }

    private fun stateOf(authorizeUrl: String): String = UriComponentsBuilder.fromUriString(authorizeUrl).build().queryParams.getFirst("state")!!

    private fun callback(query: String): String =
        mvc.get("/v1/me/contacts/github/callback$query").andExpect { status { isSeeOther() } }.andReturn().response.getHeader("Location")!!

    @Test
    fun `GitHub은 start의 state로 콜백을 받아 1회용 code를 돌려주고, 시작한 사람만 complete로 login을 인증된 연락처로 넣는다`() {
        val user = user()
        val manual = UUID.randomUUID()
        saveProfile(user, """,{"id":"$manual","kind":"github","label":"깃헙","value":"typed-by-hand"}""")
        mvc.post("/v1/me/contacts/github/start") { contentType = MediaType.APPLICATION_JSON; content = """{"platform":"ios"}""" }.andExpect { status { isUnauthorized() } }
        post(user, "/v1/me/contacts/github/start", """{"platform":"desktop"}""").andExpect { status { isBadRequest() } }

        val authorizeUrl = read(post(user, "/v1/me/contacts/github/start", """{"platform":"ios"}""").andExpect { status { isOk() } })["authorizeUrl"].asString()
        assertTrue(authorizeUrl.startsWith("https://github.com/login/oauth/authorize?client_id=test-github-client-id&redirect_uri="))
        assertFalse(authorizeUrl.contains("scope="))
        val state = stateOf(authorizeUrl)

        // 콜백은 Bearer 없이 받고 결과는 앱 주소의 1회용 code. login·토큰은 주소에 없다
        val location = callback("?state=$state&code=gh-code-1")
        assertTrue(location.startsWith("dearby://contacts/github?code="), location)
        assertFalse(location.contains("octocat"))
        val code = location.substringAfter("code=")
        // 같은 state는 다시 쓸 수 없다
        assertEquals("dearby://contacts/github?error=failed", callback("?state=$state&code=gh-code-1"))

        // 다른 사람이 code를 가져가면 400이고 code는 소모된다(남이 시작한 인가를 내가 승인해도 남의 프로필에 붙지 않는다)
        val other = user()
        assertEquals("GITHUB_LINK_EXPIRED", read(post(other, "/v1/me/contacts/github/complete", """{"code":"$code"}""").andExpect { status { isBadRequest() } })["code"].asString())
        post(user, "/v1/me/contacts/github/complete", """{"code":"$code"}""").andExpect { status { isBadRequest() } }

        // 시작한 사람이 가져가면 수동 입력값을 덮어쓰고 인증됨. 같은 연락처 id·라벨은 유지, 버전은 오른다
        val state2 = stateOf(read(post(user, "/v1/me/contacts/github/start", """{"platform":"android"}""").andExpect { status { isOk() } })["authorizeUrl"].asString())
        val code2 = callback("?state=$state2&code=gh-code-1").substringAfter("code=")
        val linked = read(post(user, "/v1/me/contacts/github/complete", """{"code":"$code2"}""").andExpect { status { isOk() } })
        val github = contact(linked, "github")!!
        assertEquals(listOf(manual.toString(), "깃헙", "octocat", true), listOf(github["id"].asString(), github["label"].asString(), github["value"].asString(), github["verified"].asBoolean()))
        assertFalse(github["verifiedAt"].isNull)
        assertEquals(2, linked["version"].asInt())
        assertEquals(true, jdbc.sql("select verified from contacts where id = ?").param(manual).query(Boolean::class.java).single())

        // 수동 저장: 같은 값(프로필 URL로 보내도 정규화되면 같다)은 인증 유지, 값을 고치면 미인증. verified는 보내도 무시된다
        val kept = saveProfile(user, """,{"id":"$manual","kind":"github","label":"","value":"https://github.com/octocat","verified":false}""")
        assertEquals(true, contact(kept, "github")!!["verified"].asBoolean())
        val changed = saveProfile(user, """,{"id":"$manual","kind":"github","label":"","value":"someone-else","verified":true}""")
        assertEquals(false, contact(changed, "github")!!["verified"].asBoolean())
        assertTrue(contact(changed, "github")!!["verifiedAt"].isNull)
        // GitHub 항목이 없으면 끝에 더한다
        saveProfile(user)
        val state3 = stateOf(read(post(user, "/v1/me/contacts/github/start", """{"platform":"ios"}""").andExpect { status { isOk() } })["authorizeUrl"].asString())
        val code3 = callback("?state=$state3&code=gh-code-1").substringAfter("code=")
        val added = read(post(user, "/v1/me/contacts/github/complete", """{"code":"$code3"}""").andExpect { status { isOk() } })
        assertEquals(listOf("phone", "email", "github"), added["contacts"].values().map { it["kind"].asString() })
        assertEquals(true, contact(added, "github")!!["verified"].asBoolean())
    }

    @Test
    fun `GitHub 콜백은 취소·실패·모르는 state를 예외 없이 플랫폼 주소로 알리고, 웹은 웹 주소로 돌아간다`() {
        val user = user()
        saveProfile(user)
        fun start(platform: String) = stateOf(read(post(user, "/v1/me/contacts/github/start", """{"platform":"$platform"}""").andExpect { status { isOk() } })["authorizeUrl"].asString())
        assertEquals("dearby://contacts/github?error=cancelled", callback("?state=${start("ios")}&error=access_denied&error_description=The+user+has+denied"))
        assertEquals("dearby://contacts/github?error=failed", callback("?state=${start("ios")}&code=unknown-code"))
        assertEquals("dearby://contacts/github?error=failed", callback("?state=${start("ios")}"))
        assertEquals("dearby://contacts/github?error=failed", callback("?state=${UUID.randomUUID()}&code=gh-code-1"))
        assertEquals("dearby://contacts/github?error=failed", callback("?state=not-a-uuid&code=gh-code-1"))
        assertEquals("dearby://contacts/github?error=failed", callback(""))
        assertTrue(callback("?state=${start("web")}&code=gh-code-1").startsWith("https://web.example.test/profile?code="))
        assertEquals("https://web.example.test/profile?error=cancelled", callback("?state=${start("web")}&error=access_denied"))
        // 프로필이 없는 사용자는 complete에서 400 PROFILE_REQUIRED
        val fresh = user()
        val code = callback("?state=${stateOf(read(post(fresh, "/v1/me/contacts/github/start", """{"platform":"ios"}""").andExpect { status { isOk() } })["authorizeUrl"].asString())}&code=gh-code-1").substringAfter("code=")
        assertEquals("PROFILE_REQUIRED", read(post(fresh, "/v1/me/contacts/github/complete", """{"code":"$code"}""").andExpect { status { isBadRequest() } })["code"].asString())
    }


    private companion object {
        val LOGINS = mapOf("gh-code-1" to "octocat")
    }
}
