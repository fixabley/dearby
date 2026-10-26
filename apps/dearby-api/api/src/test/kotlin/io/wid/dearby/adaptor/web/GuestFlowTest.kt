package io.wid.dearby.adaptor.web

import io.wid.dearby.application.guest.GuestRepository
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
class GuestFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient, @Autowired val profiles: ProfileRepository, @Autowired val guests: GuestService, @Autowired val guestRepository: GuestRepository) {

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

    // 주인이 QR 화면을 열어 받은 코드(native-v1.md "명함 QR 코드"). 처음 저장은 이 코드가 있어야 한다
    private fun qrCode(owner: UUID, cardId: String): String = read(mvc.post("/v1/cards/$cardId/qr-codes") {
        with(jwt().jwt { it.subject(owner.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
    }.andExpect { status { isCreated() } })["code"].asString()

    private fun MockHttpServletRequestDsl.code(value: String, activity: Long? = null) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code":"$value"${activity?.let { ""","activityId":$it""" } ?: ""}}"""
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
        val (owner, cardId) = ownerWithCard()
        // 없는 명함은 404이고 세션을 만들지 않는다
        val sessionsBefore = sessionCount()
        mvc.put("/v1/guest/cards/${UUID.randomUUID()}") { guest() }.andExpect { status { isNotFound() } }
        assertEquals(sessionsBefore, sessionCount())

        val activity = jdbc.sql("select id from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query(Long::class.java).single()
        // 코드가 없거나 틀리면 403이고 세션을 만들지 않는다
        mvc.put("/v1/guest/cards/$cardId") { guest() }.andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_REQUIRED") } }
        mvc.put("/v1/guest/cards/$cardId") { guest(); code(GuestService.randomToken()) }.andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_EXPIRED") } }
        assertEquals(sessionsBefore, sessionCount())
        val first = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId), activity) }.andExpect { status { isCreated() } })
        val token = first["guestToken"].asString()
        assertEquals(listOf(cardId, "saved"), listOf(first["cardId"].asString(), first["status"].asString()))
        assertTrue(GuestService.TOKEN.matches(token))
        assertEquals(0, jdbc.sql("select count(*) from guest_sessions where digest = ?").param(token).query(Int::class.java).single()) // 원문 토큰은 저장하지 않는다

        val again = read(mvc.put("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isOk() } })
        assertEquals("alreadySaved", again["status"].asString())
        assertFalse(again.has("guestToken"))

        // 저장한 세션은 웹 프록시 헤더만으로 코드 없이 명함을 본다. 다른·없어진 세션이나 헤더 없는 요청은 403
        mvc.get("/v1/cards/$cardId") { guest(token) }.andExpect { status { isOk() } }
        mvc.get("/v1/cards/$cardId") { guest(GuestService.randomToken()) }.andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_REQUIRED") } }
        mvc.get("/v1/cards/$cardId").andExpect { status { isForbidden() } }

        // 2026-10-19부터 항목은 GuestItem: 교환한 활동·프로그램 이름과 저장일, 회수 예정(저장일 + 30일)
        val wallet = read(mvc.get("/v1/guest/cards") { guest(token) }.andExpect { status { isOk() } })
        assertEquals(1, wallet["items"].size())
        val item = wallet["items"][0]
        assertEquals(listOf(cardId, activity, "Let'Swift 2026 · 개발 환경의 변화 워크숍"), listOf(item["card"]["id"].asString(), item["context"]["activityId"].asLong(), item["context"]["activityName"].asString()))
        assertEquals(OffsetDateTime.parse(item["savedAt"].asString()).plusDays(30), OffsetDateTime.parse(item["expiresAt"].asString()))
        assertFalse(item["card"].has("template"))
        assertFalse(wallet.has("shares"))

        // 회수: 저장일 + 30일이 지나면 목록에서 빠지고 행은 남는다. 다시 저장하면 되살아난다(이미 있던 세션이라 200)
        jdbc.sql("update guest_cards set saved_at = now() - interval '31 days' where session_digest = ?").param(GuestService.digest(token)).update()
        val now = OffsetDateTime.now()
        assertEquals(1, guestRepository.recallExpired(now.minusDays(30), now))
        assertEquals(0, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())
        assertEquals(1, jdbc.sql("select count(*) from guest_cards where session_digest = ? and recalled_at is not null").param(GuestService.digest(token)).query(Int::class.java).single())
        // 회수된 뒤에는 볼 수 없고, 다시 저장하려면 새 코드가 필요하다
        mvc.get("/v1/cards/$cardId") { guest(token) }.andExpect { status { isForbidden() } }
        mvc.put("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isForbidden() } }
        assertEquals("saved", read(mvc.put("/v1/guest/cards/$cardId") { guest(token); code(qrCode(owner, cardId)) }.andExpect { status { isOk() } })["status"].asString())
        assertEquals(1, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())

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
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId)) })["guestToken"].asString()
        mvc.delete("/v1/cards/$cardId") { with(jwt().jwt { it.subject(owner.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER"))) }.andExpect { status { isNoContent() } }
        assertEquals(0, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())
        mvc.put("/v1/guest/cards/$cardId") { guest(token) }.andExpect { status { isNotFound() } }

        val (anotherOwner, another) = ownerWithCard()
        mvc.put("/v1/guest/cards/$another") { guest(token); code(qrCode(anotherOwner, another)) }.andExpect { status { isOk() } }
        assertEquals(1, jdbc.sql("select count(*) from guest_cards where session_digest = ?").param(GuestService.digest(token)).query(Int::class.java).single())
    }

    @Test
    fun `세션 잇기 코드는 10분 1회용이고 세션당 하나이며 실패는 모두 404다`() {
        val (owner, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId)) })["guestToken"].asString()
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
        val (owner, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId)) })["guestToken"].asString()
        // 1분 창이 도중에 바뀔 수 있어 429가 나올 때까지 센다
        val statuses = (1..250).asSequence().map { mvc.get("/v1/guest/cards") { guest(token) }.andReturn().response.status }.takeWhile { it != 429 }.toList()
        assertTrue(statuses.size in 119..249, "429 before ${statuses.size + 1} requests")
        assertTrue(statuses.all { it == 200 })
    }

    // native-v1.md "명함 신고·차단": 게스트도 저장한 명함을 신고하고 차단한다(웹 프록시 헤더)
    @Test
    fun `게스트는 저장한 명함을 코드 없이 신고하고, 차단하면 저장이 빠지며 다시 저장하면 409다`() {
        val (owner, cardId) = ownerWithCard()
        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId)) })["guestToken"].asString()
        fun report() = mvc.post("/v1/cards/$cardId/reports") { guest(token); contentType = MediaType.APPLICATION_JSON; content = """{"reason":"SPAM"}""" }
        val first = read(report().andExpect { status { isCreated() } })
        assertEquals(first["id"].asString(), read(report().andExpect { status { isOk() } })["id"].asString())
        assertEquals(1, jdbc.sql("select count(*) from card_reports where reporter_guest_digest = ?").param(GuestService.digest(token)).query(Int::class.java).single())

        mvc.post("/v1/guest/blocks") { guest(); contentType = MediaType.APPLICATION_JSON; content = """{"cardId":"$cardId"}""" }.andExpect { status { isUnauthorized() } }
        val block = read(mvc.post("/v1/guest/blocks") { guest(token); contentType = MediaType.APPLICATION_JSON; content = """{"cardId":"$cardId"}""" }.andExpect { status { isCreated() } })
        assertEquals("주인", block["name"].asString())
        assertEquals(0, read(mvc.get("/v1/guest/cards") { guest(token) })["items"].size())
        mvc.put("/v1/guest/cards/$cardId") { guest(token); code(qrCode(owner, cardId)) }.andExpect { status { isConflict() }; jsonPath("$.code") { value("BLOCKED") } }
    }

    // native-v1.md "명함 교환" → "교환 세션 맥락"(2026-10-26): 비로그인은 찜이 없어 진행 중 블록까지만 남긴다
    @Test
    fun `게스트 저장은 지금 진행 중인 일정 블록을 맥락으로 남기고 세션은 남기지 않는다`() {
        val (owner, cardId) = ownerWithCard()
        jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, created_at, updated_at)
            select id, '게스트 맥락 컨퍼런스', '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', 'DRAFT', localtimestamp, localtimestamp from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'
            """
        ).update()
        val activity = jdbc.sql("select id from activities where name = '게스트 맥락 컨퍼런스'").query(Long::class.java).single()
        fun block(title: String, slot: String?, from: String, to: String) = jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, ?, ?, 'EVENT', 'CONFIRMED', 'TIMED', now() + ?::interval, now() + ?::interval, 'Asia/Seoul', '', 0, localtimestamp, localtimestamp) returning id
            """
        ).params(listOf<Any?>(activity, slot, title, from, to)).query(Long::class.java).single()
        block("활동 기간", "ACTIVITY", "-3 hours", "+5 hours")
        val afternoon = block("오후 세션", null, "-1 hours", "+2 hours")
        val track = jdbc.sql("insert into schedule_tracks (schedule_id, name, room, position) values (?, '트랙 A', '201호', 0) returning id").param(afternoon).query(Long::class.java).single()
        jdbc.sql("insert into schedule_sessions (schedule_id, track_id, kind, title, start_at, end_at, speakers) values (?, ?, 'TALK', '세션', now() - interval '30 minutes', now() + interval '30 minutes', '[]'::jsonb)").params(afternoon, track).update()

        val token = read(mvc.put("/v1/guest/cards/$cardId") { guest(); code(qrCode(owner, cardId), activity) }.andExpect { status { isCreated() } })["guestToken"].asString()
        val context = read(mvc.get("/v1/guest/cards") { guest(token) }.andExpect { status { isOk() } })["items"][0]["context"]
        assertEquals(listOf(activity, afternoon, "오후 세션"), listOf(context["activityId"].asLong(), context["scheduleId"].asLong(), context["scheduleTitle"].asString()))
        assertTrue(context["sessionId"].isNull && context["sessionTitle"].isNull && context["trackName"].isNull && context["room"].isNull)
        assertEquals(
            listOf(afternoon, null),
            jdbc.sql("select schedule_id, session_id from guest_cards where card_id = ?").param(UUID.fromString(cardId)).query { rs, _ -> listOf(rs.getLong(1), rs.getObject(2)) }.single(),
        )
    }
}
