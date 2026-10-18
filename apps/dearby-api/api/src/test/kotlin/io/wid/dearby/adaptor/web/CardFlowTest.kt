package io.wid.dearby.adaptor.web

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
import kotlin.test.assertTrue

// native-v1.md "명함·지갑"과 "템플릿": 발행 스냅샷, 공개 조회, 철회
@SpringBootTest
@AutoConfigureMockMvc
class CardFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient, @Autowired val profiles: ProfileRepository) {

    private val json = JsonMapper.builder().build()

    private fun MockHttpServletRequestDsl.asUser(id: UUID) {
        with(jwt().jwt { it.subject(id.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

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

        val public = read(mvc.get("/v1/cards/${card["id"].asString()}").andExpect { status { isOk() } })
        assertFalse(public.has("template"))
        assertEquals("# 발행자\n010-1111-2222 / ", public["introduction"].asString())
        mvc.get("/v1/cards/${UUID.randomUUID()}").andExpect { status { isNotFound() } }

        // 프로필을 고쳐도 발행본은 그대로
        profiles.save(profile.copy(name = "바뀐 이름"))
        assertEquals("발행자", read(mvc.get("/v1/cards/${card["id"].asString()}"))["profileName"].asString())
    }

    @Test
    fun `철회는 주인만 할 수 있고, 철회된 명함은 공개 조회에서 404다`() {
        val (user, profile) = userWithProfile("주인")
        val (other, _) = userWithProfile("남")
        val cardId = publish(user, profile)["id"].asString()
        mvc.delete("/v1/cards/$cardId") { asUser(other) }.andExpect { status { isForbidden() } }
        mvc.delete("/v1/cards/$cardId") { asUser(user) }.andExpect { status { isNoContent() } }
        mvc.get("/v1/cards/$cardId").andExpect { status { isNotFound() } }
        mvc.delete("/v1/cards/$cardId") { asUser(user) }.andExpect { status { isNotFound() } }
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
}
