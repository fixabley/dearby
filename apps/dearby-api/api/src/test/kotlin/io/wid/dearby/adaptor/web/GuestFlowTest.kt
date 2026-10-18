package io.wid.dearby.adaptor.web

import io.wid.dearby.application.guest.GuestService
import io.wid.dearby.application.profile.ProfileRepository
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.profile.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.*

// native-v1.md "웹 비로그인 명함 저장"·"홈 화면 세션 잇기": 프록시 키, 세션 토큰, 저장·상한, 세션 잇기 코드
@SpringBootTest
@AutoConfigureMockMvc
class GuestFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient, @Autowired val profiles: ProfileRepository, @Autowired val guests: GuestService) {

    private val json = JsonMapper.builder().build()
    private val key = "test-guest-proxy-secret-0123456789abcdef" // core testFixtures application.properties

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun MockHttpServletRequestDsl.guest(token: String? = null) {
        header("X-Guest-Proxy-Key", key)
        token?.let { header("X-Guest-Token", it) }
    }

    private fun ownerWithCard(): Pair<UUID, String> {
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        val contact = Contact(UUID.randomUUID(), ContactKind.EMAIL, "메일", "$user@example.com")
        val history = History(UUID.randomUUID(), HistoryKind.ORGANIZATION, null, "조직", null, null, null, null, null, "역할", LocalDate.of(2026, 1, 1), null, "", ApprovalStatus.APPROVED, null, VerificationStatus.UNVERIFIED, emptyList())
        profiles.save(Profile(user, "주인", emptyList(), "소개", listOf(contact), listOf(history), 1, OffsetDateTime.now()))
        val card = read(mvc.post("/v1/cards") {
            with(jwt().jwt { it.subject(user.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"명함","template":"{{profile.name}}","contactIds":["${contact.id}"],"historyIds":["${history.id}"]}"""
        }.andExpect { status { isCreated() } })
        return user to card["id"].asString()
    }


    @Test
    fun `프록시 키가 없거나 다르면 403이고 설정이 없으면 503이며 토큰이 없거나 모르는 토큰이면 401이다`() {
        mvc.get("/v1/guest/cards").andExpect { status { isForbidden() } }
        mvc.get("/v1/guest/cards") { header("X-Guest-Proxy-Key", "wrong-key-wrong-key-wrong-key-wrong-key") }.andExpect { status { isForbidden() } }
        assertFailsWith<ServiceUnavailableException> { guests.admit(null, key) }
        assertFailsWith<ServiceUnavailableException> { guests.admit("short", "short") }
        mvc.get("/v1/guest/cards") { guest() }.andExpect { status { isUnauthorized() } }
        mvc.get("/v1/guest/cards") { guest("not-a-token") }.andExpect { status { isUnauthorized() } }
        mvc.get("/v1/guest/cards") { guest(GuestService.randomToken()) }.andExpect { status { isUnauthorized() } }
        // 모르는 토큰의 세션 삭제도 401(웹은 쿠키만 지운다)
        mvc.delete("/v1/guest/session") { guest(GuestService.randomToken()) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `첫 저장은 세션을 만들어 201과 토큰을 주고, 이후 저장·삭제는 그 토큰으로 한다`() {
        val (_, cardId) = ownerWithCard()
        // 없는 명함은 404이고 세션을 만들지 않는다
        val sessionsBefore = sessionCount()
        mvc.put("/v1/guest/cards/${UUID.randomUUID()}") { guest() }.andExpect { status { isNotFound() } }
        assertEquals(sessionsBefore, sessionCount())

        val first = read(mvc.put("/v1/guest/cards/$cardId") { guest() }.andExpect { status { isCreated() } })
        val token = first["guestToken"].asString()
        assertEquals(listOf(cardId, "saved"), listOf(first["cardId"].asString(), first["status"].asString()))
        assertTrue(GuestService.TOKEN.matches(token))
        assertEquals(0, jdbc.sql("select count(*) from guest_sessions where digest = ?").param(token).query(Int::class.java).single()) // 원문 토큰은 저장하지 않는다

        val again = read(mvc.put("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isOk() } })
        assertEquals("alreadySaved", again["status"].asString())
        assertFalse(again.has("guestToken"))

        val wallet = read(mvc.get("/v1/guest/cards") { guest(token) }.andExpect { status { isOk() } })
        assertEquals(1, wallet["items"].size())
        assertFalse(wallet["items"][0].has("template"))
        assertFalse(wallet.has("shares"))

        mvc.delete("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isNoContent() } }
        assertEquals(0, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())

        mvc.delete("/v1/guest/session") { guest(token) }.andExpect { status { isNoContent() } }
        mvc.get("/v1/guest/cards") { guest(token) }.andExpect { status { isUnauthorized() } }
    }

    private fun sessionCount(): Int = jdbc.sql("select count(*) from guest_sessions").query(Int::class.java).single()

    @Test
    fun `철회된 명함은 목록에서 빠지고 다음 저장 때 참조가 정리된다`() {
        val (owner, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest() })["guestToken"].asString()
        mvc.delete("/v1/cards/$cardId") { with(jwt().jwt { it.subject(owner.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER"))) }.andExpect { status { isNoContent() } }
        assertEquals(0, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())
        mvc.put("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isNotFound() } }

        val (_, another) = ownerWithCard()
        mvc.put("/v1/guest/cards/$another") { guest(token) }.andExpect { status { isOk() } }
        assertEquals(1, jdbc.sql("select count(*) from guest_cards where session_digest = ?").param(GuestService.digest(token)).query(Int::class.java).single())
    }

    @Test
    fun `세션 잇기 코드는 10분 1회용이고 세션당 하나이며 실패는 모두 404다`() {
        val (_, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest() })["guestToken"].asString()
        mvc.post("/v1/guest/handoffs") { guest() }.andExpect { status { isUnauthorized() } }

        val issued = read(mvc.post("/v1/guest/handoffs") { guest(token) }.andExpect { status { isCreated() } })
        val code = issued["code"].asString()
        assertTrue(GuestService.CODE.matches(code))
        assertTrue(OffsetDateTime.parse(issued["expiresAt"].asString()).isAfter(OffsetDateTime.now().plusMinutes(9)))
        // DB에는 코드 원문도 토큰 원문도 없다
        assertEquals(0, jdbc.sql("select count(*) from guest_handoffs where digest = ? or token_ciphertext like ?").param(code).param("%$token%").query(Int::class.java).single())

        fun redeem(value: String) = mvc.post("/v1/guest/handoffs/redeem") { guest(); contentType = MediaType.APPLICATION_JSON; content = """{"code":"$value"}""" }
        redeem("garbage").andExpect { status { isNotFound() } }
        redeem(GuestService.randomToken()).andExpect { status { isNotFound() } }
        assertEquals(token, read(redeem(code).andExpect { status { isOk() } })["guestToken"].asString())
        redeem(code).andExpect { status { isNotFound() } }
        assertNull(jdbc.sql("select token_ciphertext from guest_handoffs where digest = ?").param(GuestService.digest(code)).query(String::class.java).optional().orElse(null))

        // 새로 발급하면 이전 코드는 무효
        val old = read(mvc.post("/v1/guest/handoffs") { guest(token) })["code"].asString()
        val new = read(mvc.post("/v1/guest/handoffs") { guest(token) })["code"].asString()
        redeem(old).andExpect { status { isNotFound() } }
        // 만료된 코드도 404
        jdbc.sql("update guest_handoffs set expires_at = now() - interval '1 minute' where digest = ?").param(GuestService.digest(new)).update()
        redeem(new).andExpect { status { isNotFound() } }
        // 세션을 지우면 코드도 사라진다
        val last = read(mvc.post("/v1/guest/handoffs") { guest(token) })["code"].asString()
        mvc.delete("/v1/guest/session") { guest(token) }.andExpect { status { isNoContent() } }
        redeem(last).andExpect { status { isNotFound() } }
    }

    @Test
    fun `세션당 1분에 120회를 넘으면 429다`() {
        val (_, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest() })["guestToken"].asString()
        // 1분 창이 도중에 바뀔 수 있어 429가 나올 때까지 센다
        val statuses = (1..250).asSequence().map { mvc.get("/v1/guest/cards") { guest(token) }.andReturn().response.status }.takeWhile { it != 429 }.toList()
        assertTrue(statuses.size in 119..249, "429 before ${statuses.size + 1} requests")
        assertTrue(statuses.all { it == 200 })
    }
}
