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

    // 프로필과 직접 입력 이력을 DB에 바로 넣는다(사용자 API는 api 모듈)
    private fun userWithHistory(approval: String = "PENDING", verification: String = "UNVERIFIED", withEvidence: Boolean = false): Pair<UUID, UUID> {
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        jdbc.sql("insert into profiles (user_id, name, job, introduction, version, created_at, updated_at) values (?, '검토 대상', '', '', 1, now(), now())").param(user).update()
        val history = UUID.randomUUID()
        jdbc.sql(
            """
            insert into profile_histories (id, user_id, kind, organization_name, program_name, role_name, start_date, description, position,
                                           approval_status, verification_status, created_at, updated_at)
            values (?, ?, 'ORGANIZATION', '직접 적은 조직', '직접 적은 프로그램', '운영진', '2026-01-01', '', 0, ?, ?, now(), now())
            """
        ).params(history, user, approval, verification).update()
        if (withEvidence) {
            jdbc.sql("insert into history_evidence (id, history_id, user_id, file_name, content_type, size, data, uploaded_at) values (?, ?, ?, '증명.png', 'image/png', 3, ?, now())")
                .params(UUID.randomUUID(), history, user, byteArrayOf(1, 2, 3)).update()
        }
        return user to history
    }

    @Test
    fun `승인 대기 이력을 보고, 카탈로그 id로 바꿔 승인하면 직접 입력 값이 그 항목으로 바뀐다`() {
        val (_, history) = userWithHistory()
        val organization = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"검토용 조직 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val role = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"name":"운영진 ${UUID.randomUUID()}"}""") }.andExpect { status { isCreated() } })["id"].asLong()

        val pending = read(mvc.get("/v1/admin/histories?approval=pending") { asAdmin() })["items"]
        val item = pending.values().single { it["id"].asString() == history.toString() }
        assertEquals("검토 대상", item["profileName"].asString())
        assertEquals(listOf("직접 적은 조직", null, "운영진"), listOf(item["organizationName"].asString(), item["organization"].takeUnless { it.isNull }, item["roleName"].asString()))
        // 계정 id는 싣지 않는다
        assertEquals(false, item.has("userId"))

        mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","organizationId":999999}""") }.andExpect { status { isBadRequest() } }
        val approved = read(mvc.post("/v1/admin/histories/$history/approval") { asAdmin(); body("""{"decision":"approve","organizationId":$organization,"roleId":$role}""") }.andExpect { status { isOk() } })
        assertEquals("approved", approved["approval"].asString())
        assertEquals(organization, approved["organization"]["id"].asLong())
        assertEquals(role, approved["role"]["id"].asLong())
        // 프로그램은 id를 주지 않아 직접 입력 그대로 남는다
        assertEquals("직접 적은 프로그램", approved["programName"].asString())
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
    fun `역할은 띄어쓰기만 다른 이름을 중복으로 보고, 고치고 지울 수 있다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val role = read(mvc.post("/v1/admin/roles") { asAdmin(); body("""{"name":"기획 팀장 $suffix","nameEn":"Planning Lead $suffix"}""") }.andExpect { status { isCreated() } })
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"name":"기획팀장 $suffix"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/roles") { asAdmin(); body("""{"name":"다른 역할 $suffix","nameEn":"PlanningLead $suffix"}""") }.andExpect { status { isBadRequest() } }
        val id = role["id"].asLong()
        assertEquals(listOf(id), read(mvc.get("/v1/admin/roles?q=planning lead $suffix") { asAdmin() })["items"].values().map { it["id"].asLong() })
        val updated = read(mvc.patch("/v1/admin/roles/$id") { asAdmin(); body("""{"name":"기획 리드 $suffix","nameEn":""}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("기획 리드 $suffix", true), listOf(updated["name"].asString(), updated["nameEn"].isNull))
        mvc.delete("/v1/admin/roles/$id") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/roles/$id") { asAdmin() }.andExpect { status { isNotFound() } }
    }
}
