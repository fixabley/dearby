package io.wid.dearby.admin

import io.wid.dearby.TestDatabaseConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig::class)
class HistoryReviewFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun MockHttpServletRequestDsl.asAdmin() {
        with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun MockHttpServletRequestDsl.body(value: String) {
        contentType = MediaType.APPLICATION_JSON
        content = value
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    // 프로필과 직접 입력 이력(조직 이름·역할 이름을 직접 적음, 승인 대기)을 DB에 바로 넣는다(사용자 API는 api 모듈)
    private fun userWithHistory(approval: String = "PENDING", verification: String = "UNVERIFIED", withEvidence: Boolean = false): Pair<UUID, UUID> {
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        jdbc.sql("insert into profiles (user_id, name, introduction, version, created_at, updated_at) values (?, '검토 대상', '', 1, now(), now())").param(user).update()
        val history = UUID.randomUUID()
        jdbc.sql(
            """
            insert into profile_histories (id, user_id, owner_kind, owner_name, role_name, start_date, description, position,
                                           approval_status, verification_status, created_at, updated_at)
            values (?, ?, 'ORGANIZATION', '직접 적은 조직', '운영진', '2026-01-01', '', 0, ?, ?, now(), now())
            """
        ).params(history, user, approval, verification).update()
        if (withEvidence) {
            jdbc.sql("insert into history_evidence (id, history_id, user_id, file_name, content_type, size, data, uploaded_at) values (?, ?, ?, '증명.png', 'image/png', 3, ?, now())")
                .params(UUID.randomUUID(), history, user, byteArrayOf(1, 2, 3)).update()
        }
        return user to history
    }

    @Test
    // admin-v1.md "이력 검토·역할": 이름만 적은 단위는 ownerKind·ownerId로 등록된 단위를, 직접 적은 역할은 그 단위의 roleId를 줘야 승인된다
    fun `승인 대기 이력을 보고, 등록된 단위와 그 단위의 역할로 바꿔 승인하면 직접 입력 값이 그 항목으로 바뀐다`() {
        val (_, history) = userWithHistory()
        val organization = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"검토용 조직 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val other = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"다른 조직 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val role = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"name":"운영진 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val otherRole = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$other,"name":"운영진 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()

        val pending = read(mvc.get("/v1/admin/histories?approval=pending") { asAdmin() })["items"]
        val item = pending.values().single { it["id"].asString() == history.toString() }
        assertEquals("검토 대상", item["profileName"].asString())
        assertEquals(listOf("organization", true, "직접 적은 조직", true, "운영진"), listOf(item["owner"]["kind"].asString(), item["owner"]["id"].isNull, item["owner"]["name"].asString(), item["organization"].isNull, item["roleName"].asString()))
        // 계정 id는 싣지 않는다
        assertEquals(false, item.has("userId"))

        // 단위를 고르지 않으면, 단위만 주고 역할이 직접 입력이면, 없는 단위면, 다른 단위의 역할이면 400
        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","roleId":$role}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","ownerKind":"organization","ownerId":$organization}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","ownerKind":"organization","ownerId":999999,"roleId":$role}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","ownerKind":"organization","ownerId":$organization,"roleId":$otherRole}""") }.andExpect { status { isBadRequest() } }
        val approved = read(mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","ownerKind":"organization","ownerId":$organization,"roleId":$role}""") }.andExpect { status { isOk() } })
        assertEquals("approved", approved["approval"].asString())
        assertEquals(listOf(organization, organization), listOf(approved["owner"]["id"].asLong(), approved["organization"]["id"].asLong()))
        assertEquals(listOf(role, approved["role"]["name"].asString()), listOf(approved["role"]["id"].asLong(), approved["roleName"].asString()))
        assertEquals(0, read(mvc.get("/v1/admin/histories?approval=pending") { asAdmin() })["items"].values().count { it["id"].asString() == history.toString() })
        // 쓰이는 역할은 지울 수 없다
        mvc.delete("/v1/admin/roles/$role") { asAdmin() }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `거절은 사유가 필요하고, 인증자료는 파일을 보고 확인하거나 거절한다`() {
        val (_, history) = userWithHistory(verification = "PENDING", withEvidence = true)
        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"reject"}""") }.andExpect { status { isBadRequest() } }
        val rejected = read(mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"reject","reason":"확인되지 않는 조직"}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("rejected", "확인되지 않는 조직"), listOf(rejected["approval"].asString(), rejected["approvalReason"].asString()))

        val item = read(mvc.get("/v1/admin/histories?verification=pending") { asAdmin() })["items"].values().single { it["id"].asString() == history.toString() }
        val evidence = item["evidence"].single()
        val file = mvc.get("/v1/admin/histories/$history/evidence/${evidence["id"].asString()}") { asAdmin() }.andExpect { status { isOk() } }.andReturn().response
        assertEquals("image/png", file.contentType)
        assertEquals(3, file.contentAsByteArray.size)
        assertEquals("verified", read(mvc.post("/v1/admin/histories/$history/verification") { asAdmin(); body("""{"decision":"verify"}""") }.andExpect { status { isOk() } })["verification"].asString())
        mvc.post("/v1/admin/histories/$history/verification") { asAdmin(); body("""{"decision":"maybe"}""") }.andExpect { status { isBadRequest() } }
        mvc.get("/v1/admin/histories/${UUID.randomUUID()}/evidence/${UUID.randomUUID()}") { asAdmin() }.andExpect { status { isNotFound() } }
        mvc.get("/v1/admin/histories") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }.andExpect { status { isForbidden() } }
    }

    @Test
    // admin-v1.md "이력 검토·역할": 역할은 단위 하나에 귀속되고 이름은 그 단위 안에서만 유일. 수집 역할을 고치면 curated
    fun `역할은 귀속 단위 안에서 띄어쓰기만 다른 이름을 중복으로 보고, 고치고 순서를 정하고 지울 수 있다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val organization = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"역할 조직 $suffix"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val other = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"역할 조직2 $suffix"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        // 귀속 단위는 정확히 하나, 등록된 것이어야 한다
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"name":"기획 팀장 $suffix"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"programId":1,"name":"기획 팀장 $suffix"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":999999,"name":"기획 팀장 $suffix"}""") }.andExpect { status { isBadRequest() } }
        val role = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"name":"기획 팀장 $suffix","nameEn":"Planning Lead $suffix"}""") }.andExpect { status { isCreated() } })
        assertEquals(listOf("organization", organization, "역할 조직 $suffix", "curated"), listOf(role["owner"]["kind"].asString(), role["owner"]["id"].asLong(), role["owner"]["name"].asString(), role["source"].asString()))
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"name":"기획팀장 $suffix"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"name":"다른 역할 $suffix","nameEn":"PlanningLead $suffix"}""") }.andExpect { status { isBadRequest() } }
        // 다른 단위에서는 같은 이름을 쓸 수 있다
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$other,"name":"기획팀장 $suffix"}""") }.andExpect { status { isCreated() } }
        val id = role["id"].asLong()
        val second = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"organizationId":$organization,"name":"둘째 $suffix"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        mvc.get("/v1/admin/roles?q=planning lead $suffix") { asAdmin() }.andExpect { status { isBadRequest() } }
        assertEquals(listOf(id), read(mvc.get("/v1/admin/roles?organizationId=$organization&q=planning lead $suffix") { asAdmin() })["items"].values().map { it["id"].asLong() })
        assertEquals(listOf(id, second), read(mvc.get("/v1/admin/roles?organizationId=$organization") { asAdmin() })["items"].values().map { it["id"].asLong() })
        val updated = read(mvc.patch("/v1/admin/roles/$id") { asAdmin(); body("""{"name":"기획 리드 $suffix","nameEn":"","position":5}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("기획 리드 $suffix", true), listOf(updated["name"].asString(), updated["nameEn"].isNull))
        assertEquals(listOf(second, id), read(mvc.get("/v1/admin/roles?organizationId=$organization") { asAdmin() })["items"].values().map { it["id"].asLong() })
        mvc.delete("/v1/admin/roles/$id") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/roles/$id") { asAdmin() }.andExpect { status { isNotFound() } }

        // 수집이 만든 활동 역할(collected)은 운영자가 고치면 curated가 된다
        val activity = jdbc.sql("select id from activities order by id limit 1").query(Long::class.java).single()
        val collected = jdbc.sql("insert into roles (activity_id, name, source, position, created_at, updated_at) values (?, ?, 'COLLECTED', 0, now(), now()) returning id").params(activity, "수집 역할 $suffix").query(Long::class.java).single()
        assertEquals("collected", read(mvc.get("/v1/admin/roles?activityId=$activity&q=수집 역할 $suffix") { asAdmin() })["items"].single()["source"].asString())
        assertEquals("curated", read(mvc.patch("/v1/admin/roles/$collected") { asAdmin(); body("""{"name":"고친 역할 $suffix"}""") }.andExpect { status { isOk() } })["source"].asString())
    }

    // admin-v1.md "명함 신고 — 2026-10-22": 목록(열린 것 먼저, 같은 명함 신고 수), 상태 바꾸기, 명함 내리기(메모 필수, 열린 신고는 RESOLVED)
    @Test
    fun `명함 신고는 ADMIN만 보고 처리하며 명함 내리기는 철회와 같고 열린 신고를 닫는다`() {
        val owner = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(owner).update()
        val card = UUID.randomUUID()
        jdbc.sql(
            """
            insert into business_cards (id, user_id, name, description, template, introduction, profile_name, job, contacts, histories, created_at, updated_at)
            values (?, ?, '명함', '', '', '소개', '신고된 사람', '개발자', '[]', '[]', now(), now())
            """
        ).params(card, owner).update()
        fun report(reason: String, minutesAgo: Int): UUID = UUID.randomUUID().also {
            jdbc.sql("insert into card_reports (id, card_id, reason, card_snapshot, created_at) values (?, ?, ?, ?::jsonb, now() - make_interval(mins => ?))")
                .params(it, card, reason, """{"name":"명함","profileName":"신고된 사람","job":"개발자","introduction":"소개","contacts":[{"kind":"EMAIL","label":"메일","value":"a@example.com"}]}""", minutesAgo).update()
        }
        val older = report("SPAM", 10)
        val newer = report("IMPERSONATION", 1)

        mvc.get("/v1/admin/card-reports") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }.andExpect { status { isForbidden() } }
        val open = read(mvc.get("/v1/admin/card-reports?status=OPEN") { asAdmin() }.andExpect { status { isOk() } })["items"].values().filter { it["cardId"].asString() == card.toString() }
        assertEquals(listOf(newer.toString(), older.toString()), open.map { it["id"].asString() })
        assertEquals(listOf(2, 2), open.map { it["reportCount"].asInt() })
        assertEquals(listOf("신고된 사람", "EMAIL"), listOf(open[0]["cardSnapshot"]["profileName"].asString(), open[0]["cardSnapshot"]["contacts"][0]["kind"].asString()))
        assertEquals(false, open[0]["cardWithdrawn"].asBoolean())

        // 무시하면 열린 목록에서 빠지고 같은 명함 신고 수는 그대로
        val dismissed = read(mvc.patch("/v1/admin/card-reports/$older") { asAdmin(); body("""{"status":"DISMISSED","note":"광고 아님"}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("DISMISSED", "광고 아님", 2), listOf(dismissed["status"].asString(), dismissed["note"].asString(), dismissed["reportCount"].asInt()))
        mvc.patch("/v1/admin/card-reports/$older") { asAdmin(); body("""{"status":"NOPE"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/card-reports/${UUID.randomUUID()}") { asAdmin(); body("""{"status":"RESOLVED"}""") }.andExpect { status { isNotFound() } }

        // 명함 내리기: 메모 필수, 다른 명함의 신고 id는 404. 내리면 철회되고 열린 신고만 RESOLVED. 다시 내려도 204
        mvc.post("/v1/admin/cards/$card/withdraw") { asAdmin(); body("""{"reportId":"$newer"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/cards/${UUID.randomUUID()}/withdraw") { asAdmin(); body("""{"reportId":"$newer","note":"사칭"}""") }.andExpect { status { isNotFound() } }
        mvc.post("/v1/admin/cards/$card/withdraw") { asAdmin(); body("""{"reportId":"$newer","note":"사칭"}""") }.andExpect { status { isNoContent() } }
        assertEquals(true, jdbc.sql("select revoked from business_cards where id = ?").param(card).query(Boolean::class.java).single())
        // 내린 명함의 내용은 비고(2026-10-25 "명함 지우기"), 신고 스냅샷은 근거로 남는다
        assertEquals("", jdbc.sql("select name || introduction || profile_name || job from business_cards where id = ?").param(card).query(String::class.java).single())
        val all = read(mvc.get("/v1/admin/card-reports") { asAdmin() })["items"].values().filter { it["cardId"].asString() == card.toString() }.associateBy { it["id"].asString() }
        assertEquals("신고된 사람", all[newer.toString()]!!["cardSnapshot"]["profileName"].asString())
        assertEquals(listOf("RESOLVED", "사칭", "true"), listOf(all[newer.toString()]!!["status"].asString(), all[newer.toString()]!!["note"].asString(), all[newer.toString()]!!["cardWithdrawn"].asString()))
        assertEquals("DISMISSED", all[older.toString()]!!["status"].asString())
        mvc.post("/v1/admin/cards/$card/withdraw") { asAdmin(); body("""{"note":"다시"}""") }.andExpect { status { isNoContent() } }
    }
}
