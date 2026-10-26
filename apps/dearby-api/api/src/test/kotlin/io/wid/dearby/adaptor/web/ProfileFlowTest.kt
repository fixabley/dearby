package io.wid.dearby.adaptor.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// native-v1.md "프로필·명함 v2": 프로필 데이터, 버전 이력, 단계별 이력의 승인 상태, 인증자료, 선택 창 검색
@SpringBootTest
@AutoConfigureMockMvc
class ProfileFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    // 동의(native-v1.md "개인정보 동의")까지 마친 사용자. 동의 전 상태는 아래 동의 테스트만 쓴다
    private fun user(): UUID = userWithoutConsent().also { consent(it) }
    private fun userWithoutConsent(): UUID = UUID.randomUUID().also { jdbc.sql("insert into users (id) values (?)").param(it).update() }
    private fun consent(user: UUID) = mvc.post("/v1/me/consents") {
        asUser(user); contentType = MediaType.APPLICATION_JSON; content = """{"privacyVersion":"2026-10-22","termsVersion":"2026-10-22"}"""
    }.andExpect { status { isNoContent() } }

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
        val body = """{"name":" 조민준 ","jobs":[{"id":"$jobEntry","jobName":"개발자"}],"introduction":"안녕하세요\n반갑습니다","introLink":" notion.so/me ","contacts":${contacts()},"histories":[]}"""
        val saved = read(put(user, body).andExpect { status { isOk() } })
        assertEquals(listOf(1, "조민준", user.toString()), listOf(saved["version"].asInt(), saved["name"].asString(), saved["id"].asString()))
        // 옛 앱이 보내는 소개 글은 무시하고(2026-10-26 소개 글 폐지) 소개 링크는 스킴이 없으면 https://를 붙인다
        assertEquals(listOf("", "https://notion.so/me"), listOf(saved["introduction"].asString(), saved["introLink"].asString()))
        assertEquals("phone", saved["contacts"][0]["kind"].asString())
        // 같은 내용이면 버전 그대로
        assertEquals(1, read(put(user, body).andExpect { status { isOk() } })["version"].asInt())
        // 이전 버전으로 보내면 409, 맞으면 200
        put(user, body.replace("개발자", "디자이너"), ifMatch = 0).andExpect { status { isConflict() } }
        assertEquals(2, read(put(user, body.replace("개발자", "디자이너"), ifMatch = 1).andExpect { status { isOk() } })["version"].asInt())

        // 연락처 규칙
        put(user, """{"name":"a","contacts":[{"id":"$phone","kind":"phone","label":"","value":"010-1234-5678"}]}""").andExpect { status { isBadRequest() } }
        put(user, """{"name":"a","contacts":${contacts().replace("010-1234-5678", "abc")}}""").andExpect { status { isBadRequest() } }
        // 사용자명 종류: 프로필 URL은 사용자명으로 바뀌고, 다른 주소·허용하지 않는 글자·오픈채팅 URL은 400
        val other = user() // 버전 수를 세는 아래 검사와 섞이지 않게
        val github = UUID.randomUUID()
        val (otherPhone, otherEmail) = UUID.randomUUID() to UUID.randomUUID() // 연락처 id는 전체에서 유일
        fun extra(kind: String, value: String, id: UUID = UUID.randomUUID()) =
            contacts(""",{"id":"$id","kind":"$kind","label":"","value":"$value"}""").replace("$phone", "$otherPhone").replace("$email", "$otherEmail")
        val normalized = read(put(other, """{"name":"a","contacts":${extra("github", "http://www.github.com/x-y?tab=repos", github)}}""").andExpect { status { isOk() } })
        assertEquals("x-y", normalized["contacts"][2]["value"].asString())
        assertEquals("minjun.cho", read(put(other, """{"name":"a","contacts":${extra("instagram", " @minjun.cho ")}}""").andExpect { status { isOk() } })["contacts"][2]["value"].asString())
        put(other, """{"name":"a","contacts":${extra("github", "https://gitlab.com/x")}}""").andExpect { status { isBadRequest() } }
        put(other, """{"name":"a","contacts":${extra("behance", "이름")}}""").andExpect { status { isBadRequest() } }
        put(other, """{"name":"a","contacts":${extra("kakao", "https://open.kakao.com/o/abc")}}""").andExpect { status { isBadRequest() } }
        // 이미 저장된 그대로의 옛 값(오픈채팅 URL)은 다시 저장해도 막히지 않는다
        val kakao = UUID.randomUUID()
        jdbc.sql("insert into contacts (id, user_id, kind, label, value, position) values (?, ?, 'KAKAO', '', 'https://open.kakao.com/o/abc', 2)").params(kakao, other).update()
        put(other, """{"name":"a","contacts":${extra("kakao", "https://open.kakao.com/o/abc", kakao)}}""").andExpect { status { isOk() } }
        put(user, """{"name":"","contacts":${contacts()}}""").andExpect { status { isBadRequest() } }
        // 소개 링크는 https만
        listOf("http://example.com", "javascript:alert(1)", "not a link").forEach {
            put(user, """{"name":"a","introLink":"$it","contacts":${contacts()}}""").andExpect { status { isBadRequest() } }
        }

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
        val activity = jdbc.sql("select id from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query(Long::class.java).single()
        val picked = UUID.randomUUID()
        val custom = UUID.randomUUID()
        val fromActivity = UUID.randomUUID()
        fun body(customOrganization: String) = """{"name":"이력","contacts":${contacts()},"histories":[
            {"id":"$picked","kind":"organization","organizationId":$organization,"programId":$program,"roleId":$roleId,"organizationName":"무시됨","startDate":"2026-01-01","endDate":null,"description":""},
            {"id":"$custom","kind":"activity","organizationName":"$customOrganization","activityName":"직접 적은 활동","roleName":"운영진","startDate":"2025-03-01","endDate":"2025-06-30","description":"설명"},
            {"id":"$fromActivity","kind":"activity","activityId":$activity,"roleId":$roleId,"startDate":"2026-02-01","endDate":null,"description":""}
        ]}"""
        val saved = read(put(user, body("직접 적은 조직")).andExpect { status { isOk() } })
        val byId = saved["histories"].values().associateBy { it["id"].asString() }
        val first = byId.getValue(picked.toString())
        assertEquals(listOf("approved", "unverified", organization), listOf(first["approval"].asString(), first["verification"].asString(), first["organization"]["id"].asLong()))
        // id를 주면 직접 입력 이름은 버리고 활동 정보 이름을 쓴다
        assertEquals(first["organization"]["name"].asString(), first["organizationName"].asString())
        // 활동만 고르면 소속 프로그램·조직을 서버가 채운다(2026-10-18 퍼널 분리)
        val third = byId.getValue(fromActivity.toString())
        assertEquals(listOf("approved", organization, program, activity), listOf(third["approval"].asString(), third["organization"]["id"].asLong(), third["program"]["id"].asLong(), third["activity"]["id"].asLong()))
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

    // 활동 이력에서 단기 행사 제외(native-v1.md, 2026-10-20): 활동 기간이 7일 미만인 활동은 이력에 못 넣는다. 직접 입력 이름은 제한 없음
    @Test
    fun `7일 미만 행사를 활동 이력에 넣으면 400 HISTORY_ACTIVITY_TOO_SHORT이고, 7일 이상은 저장된다`() {
        val user = user()
        val (_, program) = letswift()
        fun activity(name: String, days: Int, category: String): Long {
            val id = jdbc.sql(
                """
                insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                        freshness, source_note, publication_status, category, created_at, updated_at)
                values (?, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', 'PUBLISHED', ?, localtimestamp, localtimestamp) returning id
                """
            ).params(program, name, category).query(Long::class.java).single()
            jdbc.sql(
                """
                insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at)
                values (?, 'ACTIVITY', ?, 'EVENT', 'CONFIRMED', 'ALL_DAY', date '2025-03-01', date '2025-03-01' + ?, '', 0, localtimestamp, localtimestamp)
                """
            ).params(id, name, days).update()
            return id
        }
        val conference = activity("이력 하루 컨퍼런스 ${UUID.randomUUID()}", 1, "CONFERENCE")
        val bootcamp = activity("이력 열흘 부트캠프 ${UUID.randomUUID()}", 10, "BOOTCAMP")
        val historyId = UUID.randomUUID()
        fun body(activityId: Long, id: UUID = historyId, name: String = "이력") = """{"name":"$name","contacts":${contacts()},"histories":[{"id":"$id","kind":"activity","activityId":$activityId,"roleName":"참가자","startDate":"2025-03-01","endDate":null,"description":""}]}"""
        val denied = read(put(user, body(conference)).andExpect { status { isBadRequest() } })
        assertEquals(listOf("HISTORY_ACTIVITY_TOO_SHORT", "일주일 미만 행사는 활동 이력에 넣을 수 없어요"), listOf(denied["code"].asString(), denied["detail"].asString()))
        assertEquals(bootcamp, read(put(user, body(bootcamp)).andExpect { status { isOk() } })["histories"].single()["activity"]["id"].asLong())
        // 이미 같은 활동으로 저장된 이력은 그 활동이 나중에 짧아져도 다시 검사하지 않는다(프로필 저장·되돌리기가 막히지 않음). 새 이력으로 넣는 것은 400
        jdbc.sql("update schedules set end_date_exclusive = start_date + 1 where activity_id = ? and slot = 'ACTIVITY'").param(bootcamp).update()
        assertEquals(2, read(put(user, body(bootcamp, name = "이름 고침")).andExpect { status { isOk() } })["version"].asInt())
        mvc.post("/v1/profile/versions/1/restore") { asUser(user) }.andExpect { status { isOk() } }
        put(user, body(bootcamp, id = UUID.randomUUID())).andExpect { status { isBadRequest() } }
        // 직접 입력한 활동 이름은 제한하지 않는다
        put(user, """{"name":"이력","contacts":${contacts()},"histories":[{"id":"${UUID.randomUUID()}","kind":"activity","organizationName":"o","activityName":"하루 컨퍼런스","roleName":"r","startDate":"2025-03-01"}]}""").andExpect { status { isOk() } }
        jdbc.sql("update activities set publication_status = 'DRAFT' where id in (?, ?)").params(conference, bootcamp).update()
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

    // native-v1.md "프로필 사진": 동의 뒤에만 올리고, 서버가 JPEG·정사각 512px 이하로 다시 인코딩하며 버전 스냅샷에는 싣지 않는다
    @Test
    fun `프로필 사진은 동의 뒤에만 올리고, 정사각 JPEG로 다시 인코딩해 photoUrl로 주며 지우면 사라진다`() {
        fun image(width: Int, height: Int, format: String, type: Int = BufferedImage.TYPE_INT_RGB) =
            ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, type), format, it) }.toByteArray()
        fun upload(who: UUID, type: String, bytes: ByteArray) = mvc.multipart(HttpMethod.PUT, "/v1/profile/photo") {
            file(MockMultipartFile("file", "photo", type, bytes)); asUser(who)
        }
        fun stored(who: UUID) = mvc.get("/v1/profile/photo") { asUser(who) }.andExpect { status { isOk() } }.andReturn().response
        fun side(bytes: ByteArray) = ImageIO.read(bytes.inputStream()).let { listOf(it.width, it.height) }

        val denied = userWithoutConsent()
        assertEquals("CONSENT_REQUIRED", read(upload(denied, "image/png", image(10, 10, "png")).andExpect { status { isForbidden() } })["code"].asString())

        val user = user()
        assertTrue(read(mvc.get("/v1/profile") { asUser(user) })["photoUrl"].isNull)
        mvc.get("/v1/profile/photo") { asUser(user) }.andExpect { status { isNotFound() } }
        upload(user, "image/webp", image(10, 10, "png")).andExpect { status { isBadRequest() } }
        upload(user, "image/png", byteArrayOf(1, 2, 3)).andExpect { status { isBadRequest() } }
        upload(user, "image/png", image(4097, 1, "png")).andExpect { status { isBadRequest() } }
        upload(user, "image/png", ByteArray(5 * 1024 * 1024 + 1)).andExpect { status { isBadRequest() } }

        // 투명 PNG·직사각형도 받아 가운데 정사각형 JPEG(512px)로 바꾼다
        val first = read(upload(user, "image/png", image(1000, 800, "png", BufferedImage.TYPE_INT_ARGB)).andExpect { status { isOk() } })["photoUrl"].asString()
        assertTrue(first.startsWith("/v1/profile/photo?v="), first)
        val photo = stored(user)
        assertEquals("image/jpeg", photo.contentType)
        assertTrue(photo.getHeader("Cache-Control")!!.contains("private"))
        assertEquals(listOf(512, 512), side(photo.contentAsByteArray))
        // 작은 사진은 키우지 않는다. 다시 올리면 v가 바뀐다
        put(user, """{"name":"사진","contacts":${contacts()},"histories":[]}""").andExpect { status { isOk() } }
        val second = read(upload(user, "image/jpeg", image(200, 200, "jpg")).andExpect { status { isOk() } })["photoUrl"].asString()
        assertEquals(listOf(200, 200), side(stored(user).contentAsByteArray))
        val profile = read(mvc.get("/v1/profile") { asUser(user) })
        assertEquals(second, profile["photoUrl"].asString())
        assertTrue(first != second)
        assertTrue(read(mvc.get("/v1/profile/versions/${profile["version"].asInt()}") { asUser(user) })["profile"]["photoUrl"].isNull)
        // 다른 사람의 사진은 볼 수 없다(본인 것만 조회)
        mvc.get("/v1/profile/photo") { asUser(user()) }.andExpect { status { isNotFound() } }

        mvc.delete("/v1/profile/photo") { asUser(user) }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/profile/photo") { asUser(user) }.andExpect { status { isNoContent() } }
        assertTrue(read(mvc.get("/v1/profile") { asUser(user) })["photoUrl"].isNull)
        mvc.get("/v1/profile/photo") { asUser(user) }.andExpect { status { isNotFound() } }
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

    @Test
    fun `프로필 쓰기는 현재 버전의 개인정보·약관 동의가 있어야 하고, 동의는 GET에 실리며 계정과 함께 지워진다`() {
        val user = userWithoutConsent()
        val before = read(mvc.get("/v1/profile") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(true, true), listOf(before["consentRequired"].asBoolean(), before["consent"].isNull))
        val denied = read(put(user, """{"name":"조민준","jobs":[],"introduction":"","contacts":${contacts()},"histories":[]}""").andExpect { status { isForbidden() } })
        assertEquals("CONSENT_REQUIRED", denied["code"].asString())
        val wrong = read(mvc.post("/v1/me/consents") {
            asUser(user); contentType = MediaType.APPLICATION_JSON; content = """{"privacyVersion":"2025-01-01","termsVersion":"2026-10-22"}"""
        }.andExpect { status { isBadRequest() } })
        assertEquals("CONSENT_VERSION", wrong["code"].asString())
        consent(user)
        val after = read(mvc.get("/v1/profile") { asUser(user) }.andExpect { status { isOk() } })
        assertEquals(listOf(false, "2026-10-22", "2026-10-22"), listOf(after["consentRequired"].asBoolean(), after["consent"]["privacyVersion"].asString(), after["consent"]["termsVersion"].asString()))
        assertFalse(after["consent"]["consentedAt"].isNull)
        val saved = read(put(user, """{"name":"조민준","jobs":[],"introduction":"","contacts":${contacts()},"histories":[]}""").andExpect { status { isOk() } })
        assertEquals(1, saved["version"].asInt())
        assertEquals(1, jdbc.sql("select count(*) from user_consents where user_id = ?").param(user).query(Long::class.java).single())
        // 문서 버전이 오르면(2026-10-19 → 2026-10-22, 내 활동 서버 저장) 이전 동의만 있는 사용자는 다시 동의해야 한다
        val earlier = userWithoutConsent()
        jdbc.sql("insert into user_consents (user_id, privacy_version, terms_version, consented_at) values (?, '2026-10-19', '2026-10-19', now())").param(earlier).update()
        assertEquals(true, read(mvc.get("/v1/profile") { asUser(earlier) })["consentRequired"].asBoolean())
    }

    @Test
    fun `광고 목적 제3자 제공 동의는 선택이라 프로필 동의와 따로 기록·철회되고, 현재 버전일 때만 agreed다`() {
        val user = userWithoutConsent()
        fun get() = read(mvc.get("/v1/me/consents/ad-third-party") { asUser(user) }.andExpect { status { isOk() } })
        fun putAd(version: String) = mvc.put("/v1/me/consents/ad-third-party") { asUser(user); contentType = MediaType.APPLICATION_JSON; content = """{"version":"$version"}""" }
        assertEquals(listOf(true, false), listOf(get()["consent"].isNull, get()["agreed"].asBoolean()))
        assertEquals("CONSENT_VERSION", read(putAd("2026-10-24").andExpect { status { isBadRequest() } })["code"].asString())
        // 프로필 동의가 없어도 기록된다(서로 묶지 않는다)
        val given = read(putAd("2026-10-25").andExpect { status { isOk() } })
        assertEquals(listOf(true, "2026-10-25"), listOf(given["agreed"].asBoolean(), given["consent"]["version"].asString()))
        fun instant(body: JsonNode) = java.time.OffsetDateTime.parse(body["consent"]["agreedAt"].asString()).toInstant()
        // 같은 버전에 다시 동의해도 처음 시각을 지킨다(응답 시간대는 저장 경로마다 다를 수 있어 시각으로 비교)
        assertEquals(instant(given), instant(read(putAd("2026-10-25").andExpect { status { isOk() } })))
        assertEquals(instant(given), instant(get()))
        assertEquals(true, read(mvc.get("/v1/profile") { asUser(user) })["consentRequired"].asBoolean())
        // 옛 버전 기록은 agreed=false(다시 묻는다)
        jdbc.sql("update user_ad_consents set version = '2026-01-01' where user_id = ?").param(user).update()
        assertEquals(listOf(false, "2026-01-01"), listOf(get()["agreed"].asBoolean(), get()["consent"]["version"].asString()))
        // 철회는 멱등이다
        repeat(2) { mvc.delete("/v1/me/consents/ad-third-party") { asUser(user) }.andExpect { status { isNoContent() } } }
        assertEquals(listOf(true, false), listOf(get()["consent"].isNull, get()["agreed"].asBoolean()))
        mvc.get("/v1/me/consents/ad-third-party").andExpect { status { isUnauthorized() } }
    }
}
