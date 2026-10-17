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
import org.springframework.test.web.servlet.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// admin-v1.md "직무": 계층 CRUD와 직접 입력 직무 승인
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig::class)
class JobAdminFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun MockHttpServletRequestDsl.asAdmin() {
        with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private fun post(path: String, body: String) = mvc.post(path) { asAdmin(); contentType = MediaType.APPLICATION_JSON; content = body }
    private fun patch(path: String, body: String) = mvc.patch(path) { asAdmin(); contentType = MediaType.APPLICATION_JSON; content = body }

    @Test
    fun `직무는 조직처럼 계층을 이루고 같은 계위의 이름 중복·순환·사용 중 삭제를 막는다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val root = read(post("/v1/admin/jobs", """{"name":"분야 $suffix","nameEn":"Field $suffix"}""").andExpect { status { isCreated() } })
        val rootId = root["id"].asLong()
        assertEquals(listOf(0, 0), listOf(root["childCount"].asInt(), root["ancestors"].size()))
        val child = read(post("/v1/admin/jobs", """{"name":"하위 $suffix","parentId":$rootId}""").andExpect { status { isCreated() } })
        val childId = child["id"].asLong()
        assertEquals("분야 $suffix", child["ancestors"][0]["name"].asString())
        // 같은 계위에서 띄어쓰기만 다른 이름은 400, 다른 계위는 허용
        post("/v1/admin/jobs", """{"name":"하위$suffix","parentId":$rootId}""").andExpect { status { isBadRequest() } }
        post("/v1/admin/jobs", """{"name":"하위 $suffix"}""").andExpect { status { isCreated() } }
        // 순환
        patch("/v1/admin/jobs/$rootId", """{"parentId":$childId}""").andExpect { status { isBadRequest() } }
        patch("/v1/admin/jobs/$rootId", """{"parentId":$rootId}""").andExpect { status { isBadRequest() } }
        // 트리 조회
        val children = read(mvc.get("/v1/admin/jobs?parentId=$rootId") { asAdmin() }.andExpect { status { isOk() } })
        assertEquals(childId, children["items"][0]["id"].asLong())
        assertEquals(1, read(mvc.get("/v1/admin/jobs?q=분야 $suffix") { asAdmin() })["items"][0]["childCount"].asInt())
        // 하위가 있으면 삭제 400, 프로필이 쓰면 400
        mvc.delete("/v1/admin/jobs/$rootId") { asAdmin() }.andExpect { status { isBadRequest() } }
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        jdbc.sql("insert into profile_jobs (id, user_id, job_id, position, approval_status, created_at, updated_at) values (?, ?, ?, 0, 'APPROVED', now(), now())").params(UUID.randomUUID(), user, childId).update()
        mvc.delete("/v1/admin/jobs/$childId") { asAdmin() }.andExpect { status { isBadRequest() } }
        jdbc.sql("delete from profile_jobs where user_id = ?").param(user).update()
        patch("/v1/admin/jobs/$childId", """{"name":"옮긴 $suffix","clearParent":true}""").andExpect { status { isOk() } }
        mvc.delete("/v1/admin/jobs/$childId") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/jobs/$rootId") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/jobs/$rootId") { asAdmin() }.andExpect { status { isNotFound() } }
        mvc.get("/v1/admin/jobs").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `직접 입력한 직무는 프로필 이름과 함께 검토되고, 승인 때 직무를 지정하면 그 직무로 바뀐다`() {
        val user = UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        jdbc.sql("insert into profiles (user_id, name, introduction, version, created_at, updated_at) values (?, '검토 대상', '', 1, now(), now())").param(user).update()
        val entry = UUID.randomUUID()
        val other = UUID.randomUUID()
        jdbc.sql("insert into profile_jobs (id, user_id, job_name, position, approval_status, created_at, updated_at) values (?, ?, '커뮤니티 빌더', 0, 'PENDING', now(), now())").params(entry, user).update()
        jdbc.sql("insert into profile_jobs (id, user_id, job_name, position, approval_status, created_at, updated_at) values (?, ?, '거절될 직무', 1, 'PENDING', now(), now())").params(other, user).update()
        val pending = read(mvc.get("/v1/admin/job-entries?approval=pending&size=100") { asAdmin() }.andExpect { status { isOk() } })
        val mine = pending["items"].values().first { it["id"].asString() == entry.toString() }
        assertEquals(listOf("검토 대상", "커뮤니티 빌더", true), listOf(mine["profileName"].asString(), mine["jobName"].asString(), mine["job"].isNull))
        assertTrue(mine["userId"] == null)

        val backend = jdbc.sql("select id from jobs where name = '백엔드'").query(Long::class.java).single()
        post("/v1/admin/job-entries/$entry/approval", """{"decision":"approve","jobId":999999}""").andExpect { status { isBadRequest() } }
        val approved = read(post("/v1/admin/job-entries/$entry/approval", """{"decision":"approve","jobId":$backend}""").andExpect { status { isOk() } })
        assertEquals(listOf("approved", backend, "백엔드"), listOf(approved["approval"].asString(), approved["job"]["id"].asLong(), approved["jobName"].asString()))
        post("/v1/admin/job-entries/$other/approval", """{"decision":"reject"}""").andExpect { status { isBadRequest() } }
        val rejected = read(post("/v1/admin/job-entries/$other/approval", """{"decision":"reject","reason":"직무가 아니에요"}""").andExpect { status { isOk() } })
        assertEquals(listOf("rejected", "직무가 아니에요"), listOf(rejected["approval"].asString(), rejected["approvalReason"].asString()))
        post("/v1/admin/job-entries/${UUID.randomUUID()}/approval", """{"decision":"approve"}""").andExpect { status { isNotFound() } }
    }
}
