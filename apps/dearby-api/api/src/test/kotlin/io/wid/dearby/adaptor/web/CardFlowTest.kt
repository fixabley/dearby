package io.wid.dearby.adaptor.web

import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.application.profile.ProfileRepository
import io.wid.dearby.domain.ContactKind
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// native-v1.md "명함·지갑"·"템플릿"·"명함 교환"·"명함 QR 코드": 발행 스냅샷, 조회 권한과 QR 코드, 철회, 지갑 저장·맞교환·회수
@SpringBootTest
@AutoConfigureMockMvc
class CardFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient, @Autowired val profiles: ProfileRepository, @Autowired val wallet: WalletRepository) {

    private val json = JsonMapper.builder().build()

    private fun MockHttpServletRequestDsl.asUser(id: UUID) {
        with(jwt().jwt { it.subject(id.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    // 주인이 QR 화면을 열 때 받는 코드
    private fun qr(owner: UUID, cardId: String): JsonNode = read(mvc.post("/v1/cards/$cardId/qr-codes") { asUser(owner) }.andExpect { status { isCreated() } })

    private fun post(user: UUID, path: String, body: String) = mvc.post(path) { asUser(user); contentType = MediaType.APPLICATION_JSON; content = body }

    // 승인된 이력 하나와 승인 대기 이력 하나가 있는 프로필을 저장소로 바로 만든다
    private fun userWithProfile(name: String): Pair<UUID, Profile> {
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        val phone = Contact(UUID.randomUUID(), ContactKind.PHONE, "휴대폰", "010-1111-2222")
        val email = Contact(UUID.randomUUID(), ContactKind.EMAIL, "메일", "$name@example.com")
        fun history(approval: ApprovalStatus) = History(
            UUID.randomUUID(), HistoryKind.ORGANIZATION, null, "직접 적은 조직", null, null, null, null, null, "운영진",
            LocalDate.of(2026, 1, 1), null, "", approval, null, VerificationStatus.UNVERIFIED, emptyList(),
        )
        // 승인된 직무만 명함의 job에 들어간다
        val jobs = listOf(ProfileJob(UUID.randomUUID(), null, "개발자", ApprovalStatus.APPROVED, null), ProfileJob(UUID.randomUUID(), null, "디자이너", ApprovalStatus.PENDING, null))
        val profile = Profile(user, name, jobs, "안녕하세요", listOf(phone, email), listOf(history(ApprovalStatus.APPROVED), history(ApprovalStatus.PENDING)), 1, OffsetDateTime.now())
        profiles.save(profile)
        return user to profile
    }

    private fun publish(user: UUID, profile: Profile, template: String = "# {{profile.name}}\n{{contact:${profile.contacts[0].id}}} / {{contact:${profile.contacts[1].id}}}", contactIds: List<UUID> = listOf(profile.contacts[0].id), historyIds: List<UUID> = listOf(profile.histories[0].id)): JsonNode =
        read(post(user, "/v1/cards", """{"name":"내 명함","description":"설명","template":${json.writeValueAsString(template)},"contactIds":${json.writeValueAsString(contactIds)},"historyIds":${json.writeValueAsString(historyIds)}}""").andExpect { status { isCreated() } })

    @Test
    fun `발행은 고른 연락처·승인된 이력만 복사하고 템플릿을 채우며, 공개 조회에는 template이 없다`() {
        val (user, profile) = userWithProfile("발행자")
        val card = publish(user, profile)
        // 고른 연락처는 채워지고 고르지 않은 연락처 토큰은 비워진다. 이력의 evidence는 빈 목록
        assertEquals("# 발행자\n010-1111-2222 / ", card["introduction"].asString())
        assertEquals(listOf(1, 1, "발행자", "개발자"), listOf(card["contacts"].size(), card["histories"].size(), card["profileName"].asString(), card["job"].asString()))
        assertTrue(card.has("template"))
        assertEquals(0, card["histories"][0]["evidence"].size())

        // 내 프로필에 없는 id, 승인 전 이력, 빈 이름은 400. 프로필이 없는 사용자도 400
        post(user, "/v1/cards", """{"name":"x","contactIds":["${UUID.randomUUID()}"]}""").andExpect { status { isBadRequest() } }
        post(user, "/v1/cards", """{"name":"x","historyIds":["${profile.histories[1].id}"]}""").andExpect { status { isBadRequest() } }
        post(user, "/v1/cards", """{"name":" "}""").andExpect { status { isBadRequest() } }
        val noProfile = UUID.randomUUID().also { jdbc.sql("insert into users (id) values (?)").param(it).update() }
        post(noProfile, "/v1/cards", """{"name":"x"}""").andExpect { status { isBadRequest() } }

        val second = publish(user, profile, template = "둘째")
        assertEquals(listOf(card["id"].asString(), second["id"].asString()), read(mvc.get("/v1/cards") { asUser(user) }.andExpect { status { isOk() } })["items"].values().map { it["id"].asString() })
        mvc.get("/v1/cards").andExpect { status { isUnauthorized() } }

        // 처음 보는 사람은 유효한 QR 코드가 있어야 본다(native-v1.md "명함 QR 코드"). 주인은 코드 없이 본다
        val code = qr(user, card["id"].asString())["code"].asString()
        mvc.get("/v1/cards/${card["id"].asString()}").andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_REQUIRED") } }
        mvc.get("/v1/cards/${card["id"].asString()}") { asUser(user) }.andExpect { status { isOk() } }
        val public = read(mvc.get("/v1/cards/${card["id"].asString()}?code=$code").andExpect { status { isOk() } })
        assertFalse(public.has("template"))
        assertEquals("# 발행자\n010-1111-2222 / ", public["introduction"].asString())
        mvc.get("/v1/cards/${UUID.randomUUID()}").andExpect { status { isNotFound() } }

        // 프로필을 고쳐도 발행본은 그대로
        profiles.save(profile.copy(name = "바뀐 이름"))
        assertEquals("발행자", read(mvc.get("/v1/cards/${card["id"].asString()}?code=$code"))["profileName"].asString())
    }

    @Test
    fun `철회는 주인만 할 수 있고, 철회된 명함은 공개 조회에서 404다`() {
        val (user, profile) = userWithProfile("주인")
        val (other, _) = userWithProfile("남")
        val cardId = publish(user, profile)["id"].asString()
        val code = qr(user, cardId)["code"].asString()
        mvc.delete("/v1/cards/$cardId") { asUser(other) }.andExpect { status { isForbidden() } }
        mvc.delete("/v1/cards/$cardId") { asUser(user) }.andExpect { status { isNoContent() } }
        // 철회된 명함은 코드가 있어도 404이고, 그 명함의 코드는 모두 끝난다
        mvc.get("/v1/cards/$cardId?code=$code").andExpect { status { isNotFound() } }
        assertEquals(0, jdbc.sql("select count(*) from card_qr_codes where card_id = ? and revoked_at is null").param(UUID.fromString(cardId)).query(Int::class.java).single())
        mvc.delete("/v1/cards/$cardId") { asUser(user) }.andExpect { status { isNotFound() } }
    }

    // native-v1.md "명함 고치기는 같은 명함을 수정"(2026-10-20)
    @Test
    fun `고치기는 같은 id로 현재 프로필 기준 스냅샷을 다시 만들고, 공개 조회·내 목록·받은 지갑에 바로 보이며, 남의·철회된 명함은 404다`() {
        val (user, profile) = userWithProfile("주인")
        val (other, _) = userWithProfile("남")
        val (receiver, _) = userWithProfile("받는이")
        val first = publish(user, profile)
        val second = publish(user, profile, template = "둘째")
        val cardId = first["id"].asString()
        jdbc.sql("insert into receipts (id, recipient_id, card_id, context, received_at) values (?, ?, ?, '{\"activityId\":null,\"label\":null}', now())").params(UUID.randomUUID(), receiver, UUID.fromString(cardId)).update()
        // 받은 계정(회수 전 Receipt)은 코드 없이 본다
        val before = read(mvc.get("/v1/cards/$cardId") { asUser(receiver) }.andExpect { status { isOk() } })
        assertEquals(before["createdAt"].asString(), before["updatedAt"].asString())

        // 프로필을 고친 뒤 PUT하면 현재 프로필이 반영된다. template을 보내지 않으면 그 명함의 template 그대로
        profiles.save(profile.copy(name = "바뀐 주인"))
        val body = """{"name":"고친 명함","description":"새 설명","contactIds":${json.writeValueAsString(listOf(profile.contacts[0].id, profile.contacts[1].id))},"historyIds":[]}"""
        val updated = read(put(user, "/v1/cards/$cardId", body).andExpect { status { isOk() } })
        assertEquals(listOf(cardId, "고친 명함", "새 설명", "바뀐 주인", 2, 0), listOf(updated["id"].asString(), updated["name"].asString(), updated["description"].asString(), updated["profileName"].asString(), updated["contacts"].size(), updated["histories"].size()))
        assertEquals(first["template"].asString(), updated["template"].asString())
        assertEquals("# 바뀐 주인\n010-1111-2222 / 주인@example.com", updated["introduction"].asString())

        // 같은 id·createdAt, updatedAt만 바뀐다. 공개 조회·내 목록(순서 유지)·받은 지갑이 고친 내용을 보인다
        val public = read(mvc.get("/v1/cards/$cardId") { asUser(receiver) }.andExpect { status { isOk() } })
        assertEquals(listOf("고친 명함", before["createdAt"].asString()), listOf(public["name"].asString(), public["createdAt"].asString()))
        assertTrue(OffsetDateTime.parse(public["updatedAt"].asString()).isAfter(OffsetDateTime.parse(public["createdAt"].asString())))
        assertFalse(public.has("template"))
        val mine = read(mvc.get("/v1/cards") { asUser(user) })["items"]
        assertEquals(listOf(cardId to "고친 명함", second["id"].asString() to "내 명함"), mine.values().map { it["id"].asString() to it["name"].asString() })
        assertEquals("고친 명함", read(mvc.get("/v1/wallet") { asUser(receiver) })["items"][0]["card"]["name"].asString())

        // 검증은 POST와 같다(400). 남의 명함·없는 명함·철회된 명함은 404
        put(user, "/v1/cards/$cardId", """{"name":" "}""").andExpect { status { isBadRequest() } }
        put(user, "/v1/cards/$cardId", """{"name":"x","contactIds":["${UUID.randomUUID()}"]}""").andExpect { status { isBadRequest() } }
        put(user, "/v1/cards/$cardId", """{"name":"x","historyIds":["${profile.histories[1].id}"]}""").andExpect { status { isBadRequest() } }
        put(other, "/v1/cards/$cardId", body).andExpect { status { isNotFound() } }
        put(user, "/v1/cards/${UUID.randomUUID()}", body).andExpect { status { isNotFound() } }
        mvc.delete("/v1/cards/$cardId") { asUser(user) }.andExpect { status { isNoContent() } }
        put(user, "/v1/cards/$cardId", body).andExpect { status { isNotFound() } }
    }

    @Test
    fun `지갑 목록은 받은 명함만 보여 주고 철회된 명함은 빠진다`() {
        val (owner, profile) = userWithProfile("주인")
        val (receiver, _) = userWithProfile("받는이")
        val cardId = publish(owner, profile)["id"].asString()
        jdbc.sql("insert into receipts (id, recipient_id, card_id, context, received_at) values (?, ?, ?, '{\"activityId\":null,\"label\":null}', now())").params(UUID.randomUUID(), receiver, UUID.fromString(cardId)).update()
        mvc.get("/v1/wallet").andExpect { status { isUnauthorized() } }

        val wallet = read(mvc.get("/v1/wallet") { asUser(receiver) }.andExpect { status { isOk() } })
        assertEquals(1, wallet["items"].size())
        assertEquals(listOf(cardId, false), listOf(wallet["items"][0]["card"]["id"].asString(), wallet["items"][0]["reciprocal"].asBoolean()))
        assertFalse(wallet["items"][0]["card"].has("template"))
        assertFalse(wallet.has("shares"))

        // 철회된 명함은 지갑에서 빠진다
        mvc.delete("/v1/cards/$cardId") { asUser(owner) }.andExpect { status { isNoContent() } }
        assertEquals(0, read(mvc.get("/v1/wallet") { asUser(receiver) })["items"].size())
    }

    private fun put(user: UUID, path: String, body: String? = null) = mvc.put(path) { asUser(user); body?.let { contentType = MediaType.APPLICATION_JSON; content = it } }

    // V5가 넣은 Let'Swift 활동(프로그램 있음)
    private fun letSwiftActivityId(): Long = jdbc.sql("select id from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query(Long::class.java).single()

    @Test
    fun `지갑 저장은 활동을 기록하고 자기 명함은 409 OWN_CARD이며, 맞교환하면 양쪽 지갑에 같은 활동으로 남고 회수 예정이 사라진다`() {
        val (owner, ownerProfile) = userWithProfile("주인")
        val (receiver, receiverProfile) = userWithProfile("받는이")
        val (stranger, _) = userWithProfile("남")
        val ownerCard = publish(owner, ownerProfile)["id"].asString()
        val receiverCard = publish(receiver, receiverProfile)["id"].asString()
        val activity = letSwiftActivityId()

        put(receiver, "/v1/wallet/cards/${UUID.randomUUID()}", """{"activityId":$activity}""").andExpect { status { isNotFound() } }
        put(owner, "/v1/wallet/cards/$ownerCard").andExpect { status { isConflict() }; jsonPath("$.code") { value("OWN_CARD") } }

        // 처음 저장은 QR 코드가 필요하다(없으면 CARD_CODE_REQUIRED, 다른 명함의 코드는 CARD_CODE_EXPIRED)
        put(receiver, "/v1/wallet/cards/$ownerCard").andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_REQUIRED") } }
        put(receiver, "/v1/wallet/cards/$ownerCard", """{"code":"${qr(receiver, receiverCard)["code"].asString()}"}""").andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_EXPIRED") } }
        // 모르는 활동 id는 400이 아니라 null로 저장한다
        val code = qr(owner, ownerCard)["code"].asString()
        val first = read(put(receiver, "/v1/wallet/cards/$ownerCard", """{"activityId":987654321,"code":"$code"}""").andExpect { status { isCreated() } })
        assertEquals(listOf(ownerCard, false, true), listOf(first["card"]["id"].asString(), first["reciprocal"].asBoolean(), first["context"]["activityId"].isNull))
        assertEquals(OffsetDateTime.parse(first["receivedAt"].asString()).plusDays(30), OffsetDateTime.parse(first["expiresAt"].asString()))
        // 이미 있으면 200, 받은 날은 그대로, 비어 있던 활동은 채운다
        val again = read(put(receiver, "/v1/wallet/cards/$ownerCard", """{"activityId":$activity}""").andExpect { status { isOk() } })
        assertEquals(first["receivedAt"].asString(), again["receivedAt"].asString())
        assertEquals(activity, again["context"]["activityId"].asLong())
        assertEquals("Let'Swift 2026 · 개발 환경의 변화 워크숍", again["context"]["activityName"].asString())
        assertTrue(again["context"]["programName"].asString().isNotBlank())

        // 맞교환: 받은 적 없는 사람은 403, 남의 명함으로는 404
        mvc.post("/v1/wallet/cards/$ownerCard/reciprocate") { asUser(stranger); contentType = MediaType.APPLICATION_JSON; content = """{"myCardId":"$receiverCard"}""" }.andExpect { status { isForbidden() } }
        mvc.post("/v1/wallet/cards/$ownerCard/reciprocate") { asUser(receiver); contentType = MediaType.APPLICATION_JSON; content = """{"myCardId":"$ownerCard"}""" }.andExpect { status { isNotFound() } }
        mvc.post("/v1/wallet/cards/$ownerCard/reciprocate") { asUser(receiver); contentType = MediaType.APPLICATION_JSON; content = """{"myCardId":"$receiverCard"}""" }
            .andExpect { status { isCreated() }; jsonPath("$.reciprocal") { value(true) } }
        val ownerWallet = read(mvc.get("/v1/wallet") { asUser(owner) })["items"]
        assertEquals(listOf(receiverCard, activity, true), listOf(ownerWallet[0]["card"]["id"].asString(), ownerWallet[0]["context"]["activityId"].asLong(), ownerWallet[0]["reciprocal"].asBoolean()))
        val receiverWallet = read(mvc.get("/v1/wallet") { asUser(receiver) })["items"]
        assertEquals(true, receiverWallet[0]["reciprocal"].asBoolean())
        assertTrue(receiverWallet[0]["expiresAt"].isNull)

        // 회수(RecallService가 batch에서 부르는 규칙): 30일이 지난 단방향만. 맞교환 쌍은 남고, 상대 명함이 철회되면 다시 단방향이 되어 회수된다
        jdbc.sql("update receipts set received_at = now() - interval '31 days' where recipient_id in (?, ?)").params(receiver, owner).update()
        val now = OffsetDateTime.now()
        assertEquals(0, wallet.recallOneWay(now.minusDays(30), now))
        mvc.delete("/v1/cards/$receiverCard") { asUser(receiver) }.andExpect { status { isNoContent() } }
        assertEquals(1, wallet.recallOneWay(now.minusDays(30), now))
        assertEquals(0, read(mvc.get("/v1/wallet") { asUser(receiver) })["items"].size())
        assertNull(wallet.findReceipt(receiver, UUID.fromString(ownerCard)))
        // 회수된 행은 남아 있고, 회수 뒤에는 볼 수 없으며 다시 저장하려면 새 코드가 필요하다. 저장하면 되살아난다(201)
        assertEquals(1, jdbc.sql("select count(*) from receipts where recipient_id = ? and recalled_at is not null").param(receiver).query(Int::class.java).single())
        mvc.get("/v1/cards/$ownerCard") { asUser(receiver) }.andExpect { status { isForbidden() } }
        put(receiver, "/v1/wallet/cards/$ownerCard").andExpect { status { isForbidden() } }
        put(receiver, "/v1/wallet/cards/$ownerCard", """{"code":"${qr(owner, ownerCard)["code"].asString()}"}""").andExpect { status { isCreated() } }
        assertEquals(1, read(mvc.get("/v1/wallet") { asUser(receiver) })["items"].size())
    }

    // native-v1.md "명함 QR 코드"(2026-10-22): 여러 명이 쓰는 코드, 최대 30분, 닫기·새로고침은 2분 뒤 만료, 원문 없이 해시만
    @Test
    fun `QR 코드는 주인만 받고 여러 명이 쓰며 닫으면 2분 뒤, 아니면 30분 뒤 만료된다`() {
        val (owner, profile) = userWithProfile("주인")
        val (other, otherProfile) = userWithProfile("남")
        val cardId = publish(owner, profile)["id"].asString()
        val otherCard = publish(other, otherProfile)["id"].asString()
        mvc.post("/v1/cards/$cardId/qr-codes").andExpect { status { isUnauthorized() } }
        mvc.post("/v1/cards/$cardId/qr-codes") { asUser(other) }.andExpect { status { isNotFound() } }

        val issued = qr(owner, cardId)
        val code = issued["code"].asString()
        val codeId = issued["id"].asString()
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(code))
        val expiresAt = OffsetDateTime.parse(issued["expiresAt"].asString())
        assertTrue(expiresAt.isAfter(OffsetDateTime.now().plusMinutes(29)) && expiresAt.isBefore(OffsetDateTime.now().plusMinutes(31)))
        // DB에는 원문이 없다
        assertEquals(0, jdbc.sql("select count(*) from card_qr_codes where code_hash = ?").param(code).query(Int::class.java).single())

        // 여러 명이 같은 코드로 본다. 다른 명함에는 쓸 수 없다
        repeat(3) { mvc.get("/v1/cards/$cardId?code=$code").andExpect { status { isOk() } } }
        mvc.get("/v1/cards/$otherCard?code=$code").andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_EXPIRED") } }

        // 새로고침 = 새 코드 + 이전 코드 닫기. 닫은 코드는 2분 동안은 쓸 수 있다
        val refreshed = qr(owner, cardId)["code"].asString()
        mvc.delete("/v1/cards/$cardId/qr-codes/$codeId") { asUser(other) }.andExpect { status { isNotFound() } }
        mvc.delete("/v1/cards/$cardId/qr-codes/$codeId") { asUser(owner) }.andExpect { status { isNoContent() } }
        val closedUntil = jdbc.sql("select expires_at from card_qr_codes where id = ?").param(UUID.fromString(codeId)).query(OffsetDateTime::class.java).single()
        assertTrue(closedUntil.isBefore(OffsetDateTime.now().plusMinutes(2).plusSeconds(5)) && closedUntil.isAfter(OffsetDateTime.now().plusMinutes(1)))
        mvc.get("/v1/cards/$cardId?code=$code").andExpect { status { isOk() } }
        mvc.get("/v1/cards/$cardId?code=$refreshed").andExpect { status { isOk() } }
        // 두 번 닫아도 2분 뒤를 늘리지 않고 204. 모르는 코드 id도 204
        mvc.delete("/v1/cards/$cardId/qr-codes/$codeId") { asUser(owner) }.andExpect { status { isNoContent() } }
        assertTrue(!jdbc.sql("select expires_at from card_qr_codes where id = ?").param(UUID.fromString(codeId)).query(OffsetDateTime::class.java).single().isAfter(closedUntil))
        mvc.delete("/v1/cards/$cardId/qr-codes/${UUID.randomUUID()}") { asUser(owner) }.andExpect { status { isNoContent() } }

        // 2분(또는 30분)이 지나면 만료
        jdbc.sql("update card_qr_codes set expires_at = now() - interval '1 second' where id = ?").param(UUID.fromString(codeId)).update()
        mvc.get("/v1/cards/$cardId?code=$code").andExpect { status { isForbidden() }; jsonPath("$.code") { value("CARD_CODE_EXPIRED") } }
        mvc.get("/v1/cards/$cardId?code=$refreshed").andExpect { status { isOk() } }
        // 이미 만료된 코드를 닫아도 204
        mvc.delete("/v1/cards/$cardId/qr-codes/$codeId") { asUser(owner) }.andExpect { status { isNoContent() } }
    }
}
