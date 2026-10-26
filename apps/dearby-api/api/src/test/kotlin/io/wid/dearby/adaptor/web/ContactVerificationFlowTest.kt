package io.wid.dearby.adaptor.web

import io.wid.dearby.adaptor.github.GitHubProperties
import io.wid.dearby.adaptor.github.RestGitHubAccounts
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.*
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// native-v1.md "연락처 인증": GitHub 로그인 연결(start → 콜백 → code → complete)과 이메일 인증번호. GitHub 서버는 가짜 login 조회로, 메일 발송기는 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContactVerificationFlowTest.FakeGitHub::class)
class ContactVerificationFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    @MockitoBean
    lateinit var mailSender: JavaMailSender

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

    @Test
    fun `이메일은 인증번호 메일을 보내고, 맞는 번호로 연락처를 인증하며, 틀린 번호 5번이면 폐기하고 같은 이메일은 1분에 한 번만 보낸다`() {
        val user = user()
        val emailId = UUID.randomUUID()
        saveProfile(user, email = "old@example.com", emailId = emailId, label = "메일")
        post(user, "/v1/me/contacts/email/challenges", """{"email":"not-an-email"}""").andExpect { status { isBadRequest() } }
        mvc.post("/v1/me/contacts/email/challenges") { contentType = MediaType.APPLICATION_JSON; content = """{"email":"a@b.co"}""" }.andExpect { status { isUnauthorized() } }

        val challenge = read(post(user, "/v1/me/contacts/email/challenges", """{"email":" New@Example.com "}""").andExpect { status { isOk() } })
        val challengeId = challenge["challengeId"].asString()
        assertFalse(challenge["expiresAt"].isNull)
        val captor = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        verify(mailSender).send(captor.capture())
        val mail = captor.value
        assertEquals(listOf("New@Example.com"), mail.to?.toList())
        assertEquals("Dearby <no-reply@example.test>", mail.from)
        val code = Regex("^\\[Dearby] 이메일 인증번호 (\\d{6})$").matchEntire(mail.subject!!)!!.groupValues[1]
        assertTrue(mail.text!!.contains("인증번호: $code"))
        // 코드는 DB에 그대로 남지 않는다
        assertFalse(jdbc.sql("select code_hash from email_contact_challenges where id = ?").param(UUID.fromString(challengeId)).query(String::class.java).single().contains(code))

        // 같은 이메일은 1분에 한 번(대소문자 무시)
        post(user, "/v1/me/contacts/email/challenges", """{"email":"new@example.com"}""").andExpect { status { isTooManyRequests() } }

        // 틀린 번호: 남은 횟수가 줄고 다른 사람의 challenge는 쓸 수 없다
        val wrong = if (code == "000000") "000001" else "000000"
        val mismatch = read(post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$challengeId","code":"$wrong"}""").andExpect { status { isBadRequest() } })
        assertEquals("CODE_MISMATCH", mismatch["code"].asString())
        assertEquals(1, jdbc.sql("select attempts from email_contact_challenges where id = ?").param(UUID.fromString(challengeId)).query(Int::class.java).single())
        val other = user()
        assertEquals("CHALLENGE_EXPIRED", read(post(other, "/v1/me/contacts/email/verify", """{"challengeId":"$challengeId","code":"$code"}""").andExpect { status { isBadRequest() } })["code"].asString())

        // 맞는 번호: 기존 이메일 항목(id·라벨 유지)의 값을 바꾸고 인증됨, 버전이 오른다
        val verified = read(post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$challengeId","code":" $code "}""").andExpect { status { isOk() } })
        val email = contact(verified, "email")!!
        assertEquals(listOf(emailId.toString(), "메일", "New@Example.com", true), listOf(email["id"].asString(), email["label"].asString(), email["value"].asString(), email["verified"].asBoolean()))
        assertEquals(2, verified["version"].asInt())
        // 쓴 challenge는 다시 쓸 수 없다
        post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$challengeId","code":"$code"}""").andExpect { status { isBadRequest() } }
        // 같은 값으로 다시 저장해도(대소문자 달라도) 인증 유지
        assertEquals(true, contact(saveProfile(user, email = "new@example.com", emailId = emailId, label = "메일"), "email")!!["verified"].asBoolean())
        assertEquals(false, contact(saveProfile(user, email = "other@example.com", emailId = emailId, label = "메일"), "email")!!["verified"].asBoolean())

        // 5번 틀리면 폐기. 1분 제한에 걸리지 않게 다른 이메일로
        val second = read(post(user, "/v1/me/contacts/email/challenges", """{"email":"second@example.com"}""").andExpect { status { isOk() } })["challengeId"].asString()
        val captor2 = ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        verify(mailSender, org.mockito.Mockito.times(2)).send(captor2.capture())
        val code2 = captor2.allValues.last().subject!!.takeLast(6)
        repeat(4) { post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$second","code":"999999"}""").andExpect { status { isBadRequest() } } }
        val discarded = read(post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$second","code":"999999"}""").andExpect { status { isBadRequest() } })
        assertEquals("CHALLENGE_EXPIRED", discarded["code"].asString())
        assertEquals("CHALLENGE_EXPIRED", read(post(user, "/v1/me/contacts/email/verify", """{"challengeId":"$second","code":"$code2"}""").andExpect { status { isBadRequest() } })["code"].asString())
        assertFalse(contact(read(mvc.get("/v1/profile") { asUser(user) }), "email")!!["value"].asString().contains("second"))

        // 프로필이 없으면 보내지 않는다
        assertEquals("PROFILE_REQUIRED", read(post(user(), "/v1/me/contacts/email/challenges", """{"email":"third@example.com"}""").andExpect { status { isBadRequest() } })["code"].asString())
        assertNull(jdbc.sql("select id from email_contact_challenges where lower(email) = 'third@example.com'").query(UUID::class.java).optional().orElse(null))

        // 메일을 보내지 못하면 503이고 발송 횟수에 세지 않는다
        doThrow(MailSendException("down")).`when`(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage::class.java))
        post(user, "/v1/me/contacts/email/challenges", """{"email":"fourth@example.com"}""").andExpect { status { isServiceUnavailable() } }
        assertNull(jdbc.sql("select id from email_contact_challenges where lower(email) = 'fourth@example.com'").query(UUID::class.java).optional().orElse(null))
    }

    private companion object {
        val LOGINS = mapOf("gh-code-1" to "octocat")
    }
}
