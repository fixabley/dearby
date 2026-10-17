package io.wid.dearby.adaptor.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// native-v1.md "프로필·명함 v2": 프로필 데이터, 버전 이력, 단계별 이력의 승인 상태, 인증자료, 선택 창 검색
@SpringBootTest
@AutoConfigureMockMvc
class ProfileFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun user(): UUID = UUID.randomUUID().also { jdbc.sql("insert into users (id) values (?)").param(it).update() }

    private fun MockHttpServletRequestDsl.asUser(id: UUID) {
        with(jwt().jwt { it.subject(id.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun read(result: ResultActionsDsl): JsonNode = json.readTree(result.andReturn().response.contentAsString)

    private val phone = UUID.randomUUID()
    private val email = UUID.randomUUID()

    private fun contacts(extra: String = "") = """[{"id":"$phone","kind":"phone","label":"휴대폰","value":"010-1234-5678"},{"id":"$email","kind":"email","label":"메일","value":"me@example.com"}$extra]"""

    private fun put(user: UUID, body: String, ifMatch: Int? = null) = mvc.put("/v1/profile") {
        asUser(user); contentType = MediaType.APPLICATION_JSON; content = body
        ifMatch?.let { header("If-Match", it.toString()) }
    }

    private fun letswift(): Pair<Long, Long> {
        val program = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val organization = jdbc.sql("select organization_id from programs where id = ?").param(program).query(Long::class.java).single()
        return organization to program
    }

    private fun role(name: String): Long =
        jdbc.sql("insert into roles (name, created_at, updated_at) values (?, now(), now()) returning id").param(name).query(Long::class.java).single()

    @Test
    fun `빈 프로필은 version 0이고, 저장마다 버전이 오르며 같은 내용은 버전을 올리지 않는다`() {
        val user = user()
        val empty = read(mvc.get("/v1/profile") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(0, "", 0), listOf(empty["version"].asInt(), empty["name"].asString(), empty["contacts"].size()))
        mvc.get("/v1/profile").andExpect { status { isUnauthorized() } }

        val jobEntry = UUID.randomUUID()
        val body = """{"name":" 조민준 ","jobs":[{"id":"$jobEntry","jobName":"개발자"}],"introduction":"안녕하세요\n반갑습니다","contacts":${contacts()},"histories":[]}"""
        val saved = read(put(user, body).andExpect { status { isOk() } })
        assertEquals(listOf(1, "조민준", user.toString()), listOf(saved["version"].asInt(), saved["name"].asString(), saved["id"].asString()))
        assertEquals("phone", saved["contacts"][0]["kind"].asString())
        // 같은 내용이면 버전 그대로
        assertEquals(1, read(put(user, body).andExpect { status { isOk() } })["version"].asInt())
        // 이전 버전으로 보내면 409, 맞으면 200
        put(user, body.replace("개발자", "디자이너"), ifMatch = 0).andExpect { status { isConflict() } }
        assertEquals(2, read(put(user, body.replace("개발자", "디자이너"), ifMatch = 1).andExpect { status { isOk() } })["version"].asInt())

        // 연락처 규칙
        put(user, """{"name":"a","contacts":[{"id":"$phone","kind":"phone","label":"","value":"010-1234-5678"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts().replace("010-1234-5678", "abc")}}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts(""",{"id":"${UUID.randomUUID()}","kind":"github","label":"깃헙","value":"http://github.com/x"}""")}}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"","contacts":${contacts()}}""").andExpect { status { isBadRequest() } }

        val versions = read(mvc.get("/v1/profile/versions") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(2, 1), versions["items"].values().map { it["version"].asInt() })
        val first = read(mvc.get("/v1/profile/versions/1") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals("개발자", first["profile"]["jobs"][0]["jobName"].asString())
        mvc.get("/v1/profile/versions/9") { asUser(user) }.andExpect { status { isNotFound() } }
        // 되돌리기는 새 버전을 만든다
        val restored = read(mvc.post("/v1/profile/versions/1/restore") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(3, "개발자"), listOf(restored["version"].asInt(), restored["jobs"][0]["jobName"].asString()))
        assertEquals(3, jdbc.sql("select count(*) from profile_versions where user_id = ?").param(user).query(Int::class.java).single())
    }

    @Test
    fun `활동 정보 항목만 쓴 이력은 바로 승인되고, 직접 입력은 승인 대기이며 고치면 다시 대기한다`() {
        val user = user()
        val (organization, program) = letswift()
        val roleId = role("참가자 ${UUID.randomUUID()}")
        val picked = UUID.randomUUID()
        val custom = UUID.randomUUID()
        fun body(customOrganization: String) = """{"name":"이력","contacts":${contacts()},"histories":[
            {"id":"$picked","kind":"organization","organizationId":$organization,"programId":$program,"roleId":$roleId,"organizationName":"무시됨","startDate":"2026-01-01","endDate":null,"description":""},
            {"id":"$custom","kind":"activity","organizationName":"$customOrganization","activityName":"직접 적은 활동","roleName":"운영진","startDate":"2025-03-01","endDate":"2025-06-30","description":"설명"}
        ]}"""
        val saved = read(put(user, body("직접 적은 조직")).andExpect { status { isOk() } })
        val byId = saved["histories"].values().associateBy { it["id"].asString() }
        val first = byId.getValue(picked.toString())
        assertEquals(listOf("approved", "unverified", organization), listOf(first["approval"].asString(), first["verification"].asString(), first["organization"]["id"].asLong()))
        // id를 주면 직접 입력 이름은 버리고 활동 정보 이름을 쓴다
        assertEquals(first["organization"]["name"].asString(), first["organizationName"].asString())
        val second = byId.getValue(custom.toString())
        assertEquals(listOf("pending", true, "직접 적은 조직"), listOf(second["approval"].asString(), second["organization"].isNull, second["organizationName"].asString()))

        // 관리자가 승인한 뒤 그대로 저장하면 유지, 직접 입력을 고치면 다시 대기
        jdbc.sql("update profile_histories set approval_status = 'APPROVED' where id = ?").param(custom).update()
        assertEquals("approved", read(put(user, body("직접 적은 조직").replace("\"설명\"", "\"설명을 고침\"")).andExpect { status { isOk() } })["histories"].values().single { it["id"].asString() == custom.toString() }["approval"].asString())
        assertEquals("pending", read(put(user, body("다른 조직")).andExpect { status { isOk() } })["histories"].values().single { it["id"].asString() == custom.toString() }["approval"].asString())

        // 규칙: 조직·역할 필수, 활동 종류는 활동 필수, 종료일은 시작일 뒤, 없는 활동 정보 id는 400
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"organization","roleName":"x","startDate":"2026-01-01"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"organization","organizationName":"o","startDate":"2026-01-01"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"activity","organizationName":"o","roleName":"r","startDate":"2026-01-01"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"organization","organizationName":"o","roleName":"r","startDate":"2026-01-01","endDate":"2025-12-31"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"organization","organizationId":999999,"roleName":"r","startDate":"2026-01-01"}]}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `인증자료는 종류·크기·개수를 지키고, 올리면 확인 대기가 되며 다 지우면 미인증으로 돌아간다`() {
        val user = user()
        val other = user()
        val history = UUID.randomUUID()
        put(user, """{"name":"a","contacts":${contacts()},"histories":[{"id":"$history","kind":"organization","organizationName":"o","roleName":"r","startDate":"2026-01-01"}]}""").andExpect { status { isOk() } }
        fun upload(who: UUID, name: String, type: String, bytes: ByteArray) = mvc.multipart("/v1/profile/histories/$history/evidence") {
            file(MockMultipartFile("file", name, type, bytes)); asUser(who)
        }
        upload(user, "a.txt", "text/plain", byteArrayOf(1)).andExpect { status { isBadRequest() } }
        upload(user, "big.png", "image/png", ByteArray(5 * 1024 * 1024 + 1)).andExpect { status { isBadRequest() } }
        upload(other, "a.png", "image/png", byteArrayOf(1)).andExpect { status { isNotFound() } }
        val first = read(upload(user, "증명.png", "image/png", byteArrayOf(1, 2, 3)).andExpect { status { isCreated() } })
        assertEquals(listOf("증명.png", 3), listOf(first["fileName"].asString(), first["size"].asInt()))
        upload(user, "b.pdf", "application/pdf", byteArrayOf(1)).andExpect { status { isCreated() } }
        upload(user, "c.webp", "image/webp", byteArrayOf(1)).andExpect { status { isCreated() } }
        upload(user, "d.jpg", "image/jpeg", byteArrayOf(1)).andExpect { status { isBadRequest() } }

        val profile = read(mvc.get("/v1/profile") { asUser(user) })
        val item = profile["histories"].single()
        assertEquals(listOf("pending", 3), listOf(item["verification"].asString(), item["evidence"].size()))
        val file = mvc.get("/v1/profile/histories/$history/evidence/${first["id"].asString()}") { asUser(user) }.andExpect { status { isOk() } }.andReturn().response
        assertEquals("image/png", file.contentType)
        assertTrue(file.getHeader("Content-Disposition")!!.startsWith("inline"))
        mvc.get("/v1/profile/histories/$history/evidence/${first["id"].asString()}") { asUser(other) }.andExpect { status { isNotFound() } }

        for (id in item["evidence"].values().map { it["id"].asString() }) {
            mvc.delete("/v1/profile/histories/$history/evidence/$id") { asUser(user) }.andExpect { status { isNoContent() } }
        }
        assertEquals("unverified", read(mvc.get("/v1/profile") { asUser(user) })["histories"].single()["verification"].asString())
        // 확인된 이력은 자료를 지워도 유지된다
        jdbc.sql("update profile_histories set verification_status = 'VERIFIED' where id = ?").param(history).update()
        val again = read(upload(user, "e.png", "image/png", byteArrayOf(1)).andExpect { status { isCreated() } })
        assertEquals("verified", read(mvc.get("/v1/profile") { asUser(user) })["histories"].single()["verification"].asString())
        mvc.delete("/v1/profile/histories/$history/evidence/${again["id"].asString()}") { asUser(user) }.andExpect { status { isNoContent() } }
        assertEquals("verified", read(mvc.get("/v1/profile") { asUser(user) })["histories"].single()["verification"].asString())
    }

    @Test
    fun `선택 창 검색은 로그인이 필요하고 조직·프로그램·역할을 keyset 페이지로 준다`() {
        val user = user()
        mvc.get("/v1/roles").andExpect { status { isUnauthorized() } }
        val suffix = UUID.randomUUID().toString().take(8)
        role("검색 역할 $suffix")
        val roles = read(mvc.get("/v1/roles?q=검색 역할 $suffix") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(1, roles["items"].size())
        val organizations = read(mvc.get("/v1/organizations?q=Let&rootsOnly=true") { asUser(user) }.andExpect { status { isOk() } })
        assertTrue(organizations["items"].size() >= 1)
        val programs = read(mvc.get("/v1/programs?q=Let&size=1") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(organizations["items"][0]["name"].asString(), programs["items"][0]["organization"]["name"].asString())
        mvc.get("/v1/programs?size=0") { asUser(user) }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `직무는 검색해 고르면 승인되고 직접 입력은 승인 대기이며, 명함용 job은 승인된 이름만 잇는다`() {
        val user = user()
        val backend = jdbc.sql("select id from jobs where name = '백엔드'").query(Long::class.java).single()
        val design = jdbc.sql("select id from jobs where name = '프로덕트 디자인'").query(Long::class.java).single()
        val picked = UUID.randomUUID()
        val custom = UUID.randomUUID()
        fun body(customName: String, extra: String = "") = """{"name":"직무","contacts":${contacts()},"jobs":[{"id":"$picked","jobId":$backend,"jobName":"무시됨"},{"id":"$custom","jobName":"$customName"}$extra]}"""
        val saved = read(put(user, body("커뮤니티 빌더")).andExpect { status { isOk() } })
        assertEquals(listOf("approved", "백엔드", backend), listOf(saved["jobs"][0]["approval"].asString(), saved["jobs"][0]["jobName"].asString(), saved["jobs"][0]["job"]["id"].asLong()))
        assertEquals(listOf("pending", "커뮤니티 빌더", true), listOf(saved["jobs"][1]["approval"].asString(), saved["jobs"][1]["jobName"].asString(), saved["jobs"][1]["job"].isNull))
        assertEquals("백엔드", saved["job"].asString())

        // 관리자 승인 뒤 그대로 저장하면 승인 유지, 이름을 고치면 다시 대기
        jdbc.sql("update profile_jobs set approval_status = 'APPROVED' where id = ?").param(custom).update()
        assertEquals("approved", read(put(user, body("커뮤니티 빌더", """,{"id":"${UUID.randomUUID()}","jobId":$design}""")))["jobs"][1]["approval"].asString())
        val changed = read(put(user, body("커뮤니티 운영자")))
        assertEquals(listOf("pending", "백엔드"), listOf(changed["jobs"][1]["approval"].asString(), changed["job"].asString()))

        // 규칙: 없는 직무 400, 같은 직무 두 번 400, 비어 있는 직접 입력 400, 11개 400
        put(user, body("x", """,{"id":"${UUID.randomUUID()}","jobId":999999}""")).andExpect { status { isBadRequest() } }
        put(user, body("x", """,{"id":"${UUID.randomUUID()}","jobId":$backend}""")).andExpect { status { isBadRequest() } }
        put(user, """{"name":"직무","contacts":${contacts()},"jobs":[{"id":"${UUID.randomUUID()}","jobName":" "}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"직무","contacts":${contacts()},"jobs":[${(1..11).joinToString(",") { """{"id":"${UUID.randomUUID()}","jobName":"j$it"}""" }}]}""").andExpect { status { isBadRequest() } }

        // 선택 창 검색: 바로 위 직무가 parent로 온다
        val found = read(mvc.get("/v1/jobs?q=백엔드") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(backend, "개발"), listOf(found["items"][0]["id"].asLong(), found["items"][0]["parent"]["name"].asString()))
        assertTrue(read(mvc.get("/v1/jobs?rootsOnly=true&size=100") { asUser(user) })["items"].size() >= 11)
        mvc.get("/v1/jobs").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `V18 이전 버전 스냅샷의 직함 문자열은 승인된 직접 입력 직무로 읽히고 되돌릴 수 있다`() {
        val user = user()
        put(user, """{"name":"지금","contacts":${contacts()}}""").andExpect { status { isOk() } }
        val snapshot = """{"name":"옛날","job":"옛 직함","introduction":"","contacts":${json.writeValueAsString(contacts().replace("\"phone\"", "\"PHONE\"").replace("\"email\"", "\"EMAIL\""))},"histories":"[]"}""" // 스냅샷 형식은 ProfileJson(대문자 kind)
        jdbc.sql("insert into profile_versions (user_id, version, snapshot, created_at) values (?, 7, ?::jsonb, now())").params(user, snapshot).update()
        val old = read(mvc.get("/v1/profile/versions/7") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf("옛 직함", "approved"), listOf(old["profile"]["jobs"][0]["jobName"].asString(), old["profile"]["jobs"][0]["approval"].asString()))
        val restored = read(mvc.post("/v1/profile/versions/7/restore") { asUser(user) }.andExpect { status { isOk() } })
        // 되돌린 직무는 지금 규칙대로 직접 입력이라 승인 대기가 된다
        assertEquals(listOf("옛날", "옛 직함", "pending"), listOf(restored["name"].asString(), restored["jobs"][0]["jobName"].asString(), restored["jobs"][0]["approval"].asString()))
    }
}
