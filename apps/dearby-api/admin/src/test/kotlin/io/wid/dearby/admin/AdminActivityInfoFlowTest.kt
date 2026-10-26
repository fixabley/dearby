package io.wid.dearby.admin

import io.wid.dearby.TestDatabaseConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

// admin 앱은 io.wid.dearby.admin·auth만 스캔하므로 공유 테스트 DB 설정(스키마 비우고 다시 마이그레이션)을 직접 가져온다
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig::class)
class AdminActivityInfoFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    // 장소 검색 제공자 대신 고정 결과를 준다(실제 네트워크 없음). admin-v1.md "활동 장소와 지오코딩"
    @TestConfiguration
    class FakeGeocode {
        @Bean
        @Primary
        fun fakeGeocoder() = object : Geocoder {
            override fun search(query: String) = listOf(
                GeocodeResult("코엑스", "서울 강남구 영동대로 513", 37.5116828, 127.0594, "fake"),
                GeocodeResult("코엑스몰", "서울 강남구 영동대로 513 코엑스몰", 37.5100, 127.0590, "fake"),
            )
        }
    }

    private val json = JsonMapper.builder().build()

    // JsonNode 배열을 Kotlin 목록으로(assertEquals의 타입 추론용)
    private fun tagNames(node: JsonNode): List<String> = node.toList().map { it["name"].asString() }
    private fun tagNamesEn(node: JsonNode): List<String?> = node.toList().map { it["nameEn"]?.takeUnless { n -> n.isNull }?.asString() }
    private fun idList(node: JsonNode): List<Long> = node.toList().map { it.asLong() }

    private fun MockHttpServletRequestDsl.asAdmin() {
        with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun MockHttpServletRequestDsl.body(value: String) {
        contentType = MediaType.APPLICATION_JSON
        content = value
    }

    private fun read(result: org.springframework.test.web.servlet.ResultActionsDsl): JsonNode =
        json.readTree(result.andReturn().response.contentAsString)

    @Test
    fun `토큰이 없으면 401, ADMIN이 아니면 403, ADMIN이면 200`() {
        mvc.get("/v1/admin/organizations").andExpect { status { isUnauthorized() } }
        mvc.get("/v1/admin/organizations") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
            .andExpect { status { isForbidden() } }
        mvc.get("/v1/admin/organizations") { asAdmin() }.andExpect { status { isOk() } }
        // 관리자 경로가 아닌 곳은 막는다(공개 API는 admin 앱에 없다)
        mvc.get("/v1/activities") { asAdmin() }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `조직과 프로그램을 만들고 수집 설정을 넣으면 검색 결과에 함께 보인다`() {
        jdbc.sql("delete from program_collection_settings").update()
        val organization = read(
            mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"  테스트 조직 "}""") }.andExpect { status { isCreated() } }
        )
        assertEquals("테스트 조직", organization["name"].asString())
        val organizationId = organization["id"].asLong()

        val program = read(
            mvc.post("/v1/admin/programs") {
                asAdmin()
                body("""{"organizationId":$organizationId,"name":"테스트 프로그램","collection":{"officialUrl":"https://example.org/apply","allowedHosts":["Example.org"],"intervalDays":7,"enabled":true}}""")
            }.andExpect { status { isCreated() } }
        )
        assertEquals(listOf("example.org"), program["collection"]["allowedHosts"].values().map { it.asString() })
        assertEquals(true, program["collection"]["officialOnly"].asBoolean())
        assertEquals(7, program["collection"]["intervalDays"].asInt())

        val found = read(mvc.get("/v1/admin/programs?q=테스트 프로그램&organizationId=$organizationId") { asAdmin() })["items"].single()
        assertEquals("테스트 조직", found["organization"]["name"].asString())
        assertEquals(true, found["collection"]["configured"].asBoolean())

        // 설정이 없는 프로그램은 기본값(웹 전체 검색, 매일)으로 보인다
        val letswift = read(mvc.get("/v1/admin/programs?q=Let") { asAdmin() })["items"].single()
        assertEquals(false, letswift["collection"]["configured"].asBoolean())
        assertEquals(false, letswift["collection"]["officialOnly"].asBoolean())
    }

    private fun createOrganization(name: String, parentId: Long? = null): Long = read(
        mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"$name","parentOrganizationId":${parentId ?: "null"}}""") }
            .andExpect { status { isCreated() } }
    )["id"].asLong()

    private fun MockHttpServletRequestDsl.patchBody(value: String) = body(value)

    @Test
    fun `조직도는 최상위와 하위를 나눠 주고, 이름·상위 조직을 바꾸되 순환은 막는다`() {
        val root = createOrganization("트리 최상위")
        val child = createOrganization("트리 하위", root)
        val grandchild = createOrganization("트리 손자", child)

        val roots = read(mvc.get("/v1/admin/organizations?rootsOnly=true&q=트리") { asAdmin() })["items"]
        assertEquals(listOf(root), roots.values().map { it["id"].asLong() })
        assertEquals(1, roots.single()["childCount"].asInt())
        assertEquals(listOf(child), read(mvc.get("/v1/admin/organizations?parentId=$root") { asAdmin() })["items"].values().map { it["id"].asLong() })
        // 검색 결과는 최상위부터의 상위 경로를 함께 준다
        val found = read(mvc.get("/v1/admin/organizations?q=트리 손자") { asAdmin() })["items"].single()
        assertEquals(listOf("트리 최상위", "트리 하위"), found["ancestors"].values().map { it["name"].asString() })
        assertEquals(0, roots.single()["ancestors"].size())

        // 자기 자신·자기 하위 아래로는 못 옮긴다
        mvc.patch("/v1/admin/organizations/$root") { asAdmin(); patchBody("""{"parentOrganizationId":$grandchild}""") }
            .andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/organizations/$root") { asAdmin(); patchBody("""{"parentOrganizationId":$root}""") }
            .andExpect { status { isBadRequest() } }
        // 이름만 바꾸면 상위는 그대로, clearParent면 최상위로
        val renamed = read(mvc.patch("/v1/admin/organizations/$child") { asAdmin(); patchBody("""{"name":"트리 하위(수정)"}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf("트리 하위(수정)", root.toString()), listOf(renamed["name"].asString(), renamed["parentOrganizationId"].asString()))
        val moved = read(mvc.patch("/v1/admin/organizations/$grandchild") { asAdmin(); patchBody("""{"clearParent":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(true, moved["parentOrganizationId"].isNull)
        mvc.patch("/v1/admin/organizations/999999") { asAdmin(); patchBody("""{"name":"x"}""") }.andExpect { status { isNotFound() } }

        // 프로그램은 다른 조직으로 옮기고 이름을 바꿀 수 있다. 조직의 programCount가 따라 바뀐다
        val program = read(mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$child,"name":"트리 프로그램"}""") }
            .andExpect { status { isCreated() } })["id"].asLong()
        val updated = read(mvc.patch("/v1/admin/programs/$program") { asAdmin(); patchBody("""{"organizationId":$root,"name":"트리 프로그램(이동)"}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf(root.toString(), "트리 프로그램(이동)"), listOf(updated["organization"]["id"].asString(), updated["name"].asString()))
        assertEquals(1, read(mvc.get("/v1/admin/organizations?rootsOnly=true&q=트리 최상위") { asAdmin() })["items"].single()["programCount"].asInt())
        mvc.patch("/v1/admin/programs/$program") { asAdmin(); patchBody("""{"organizationId":999999}""") }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `하위 항목이 있으면 지우지 않고, 안쪽부터 지우면 지워진다`() {
        val organization = createOrganization("삭제 조직")
        val child = createOrganization("삭제 하위", organization)
        val program = read(mvc.post("/v1/admin/programs") {
            asAdmin()
            body("""{"organizationId":$organization,"name":"삭제 프로그램","collection":{"allowedHosts":["example.org"],"intervalDays":1,"enabled":true}}""")
        }.andExpect { status { isCreated() } })["id"].asLong()
        val activity = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, source_id, created_at, updated_at)
            values (?, '삭제 활동', '', 'REGISTRATION', 'UNKNOWN', 'https://example.org', 'STALE', '', gen_random_uuid(), localtimestamp, localtimestamp)
            returning id
            """
        ).param(program).query(Long::class.java).single()
        jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', 't', 'EVENT', 'CONFIRMED', '', 0, localtimestamp, localtimestamp)")
            .param(activity).update()

        mvc.delete("/v1/admin/organizations/$organization") { asAdmin() }.andExpect { status { isBadRequest() } }
        mvc.delete("/v1/admin/programs/$program") { asAdmin() }.andExpect { status { isBadRequest() } }

        mvc.delete("/v1/admin/activities/$activity") { asAdmin() }.andExpect { status { isNoContent() } }
        // 일정은 FK로 함께 지워지고, 수집 출처는 다시 수집하지 않게 남는다
        assertEquals(0, jdbc.sql("select count(*) from schedules where activity_id = ?").param(activity).query(Int::class.java).single())
        assertEquals(1, jdbc.sql("select count(*) from dismissed_activity_sources").query(Int::class.java).single())

        mvc.delete("/v1/admin/programs/$program") { asAdmin() }.andExpect { status { isNoContent() } }
        assertEquals(0, jdbc.sql("select count(*) from program_collection_settings where program_id = ?").param(program).query(Int::class.java).single())
        mvc.delete("/v1/admin/organizations/$child") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/organizations/$organization") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/organizations/$organization") { asAdmin() }.andExpect { status { isNotFound() } }
        mvc.delete("/v1/admin/activities/$activity") { asAdmin() }.andExpect { status { isNotFound() } }
        mvc.delete("/v1/admin/programs/$program") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `같은 계위에서 띄어쓰기만 다른 이름은 등록·수정·이동이 안 된다`() {
        val parent = createOrganization("중복 상위")
        val other = createOrganization("중복 다른 상위")
        createOrganization("우아한 형제들", parent)
        // 같은 상위 아래 띄어쓰기만 다름 → 400, 다른 상위 아래는 허용
        mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"우아한형제들","parentOrganizationId":$parent}""") }
            .andExpect { status { isBadRequest() } }
        val elsewhere = createOrganization("우아한형제들", other)
        // 옮기거나 이름을 바꿔 중복이 되는 것도 막는다
        mvc.patch("/v1/admin/organizations/$elsewhere") { asAdmin(); patchBody("""{"parentOrganizationId":$parent}""") }
            .andExpect { status { isBadRequest() } }
        createOrganization("이름 바꿀 조직")
            .let { mvc.patch("/v1/admin/organizations/$it") { asAdmin(); patchBody("""{"name":"중복상위"}""") }.andExpect { status { isBadRequest() } } }

        val program = read(mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$parent,"name":"테크 코스"}""") }
            .andExpect { status { isCreated() } })["id"].asLong()
        mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$parent,"name":"테크코스"}""") }
            .andExpect { status { isBadRequest() } }
        val moving = read(mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$other,"name":"테크코스 "}""") }
            .andExpect { status { isCreated() } })["id"].asLong()
        mvc.patch("/v1/admin/programs/$moving") { asAdmin(); patchBody("""{"organizationId":$parent}""") }
            .andExpect { status { isBadRequest() } }
        // 자기 이름을 그대로 다시 보내는 것은 괜찮다
        mvc.patch("/v1/admin/programs/$program") { asAdmin(); patchBody("""{"name":"테크 코스"}""") }.andExpect { status { isOk() } }
    }

    @Test
    fun `영문 이름을 함께 넣고 고치거나 지우며, 같은 계위의 영문 이름도 중복이면 안 된다`() {
        val organization = read(mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"영문 조직","nameEn":" Woowa Brothers "}""") }
            .andExpect { status { isCreated() } })
        assertEquals("Woowa Brothers", organization["nameEn"].asString())
        val organizationId = organization["id"].asLong()
        // 영문 이름으로도 찾는다
        assertEquals(listOf(organizationId), read(mvc.get("/v1/admin/organizations?q=woowa") { asAdmin() })["items"].values().map { it["id"].asLong() })
        // 한글 이름이 달라도 같은 계위에서 띄어쓰기만 다른 영문 이름은 중복
        mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"다른 영문 조직","nameEn":"WoowaBrothers"}""") }
            .andExpect { status { isBadRequest() } }
        // 보내지 않으면 그대로, 빈 문자열이면 지운다
        assertEquals("Woowa Brothers", read(mvc.patch("/v1/admin/organizations/$organizationId") { asAdmin(); patchBody("""{"name":"영문 조직(수정)"}""") }
            .andExpect { status { isOk() } })["nameEn"].asString())
        assertEquals(true, read(mvc.patch("/v1/admin/organizations/$organizationId") { asAdmin(); patchBody("""{"nameEn":""}""") }
            .andExpect { status { isOk() } })["nameEn"].isNull)

        val program = read(mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$organizationId,"name":"영문 프로그램","nameEn":"Tech Course"}""") }
            .andExpect { status { isCreated() } })
        assertEquals("Tech Course", program["nameEn"].asString())
        mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$organizationId,"name":"다른 프로그램","nameEn":"TechCourse"}""") }
            .andExpect { status { isBadRequest() } }
        assertEquals("Tech Course 2", read(mvc.patch("/v1/admin/programs/${program["id"].asLong()}") { asAdmin(); patchBody("""{"nameEn":"Tech Course 2"}""") }
            .andExpect { status { isOk() } })["nameEn"].asString())

        // 활동은 한글·영문 이름과 설명을 고친다. 한글 이름은 비울 수 없다
        val activity = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, created_at, updated_at)
            values (?, '영문 활동', '설명', 'REGISTRATION', 'UNKNOWN', 'https://example.org', 'STALE', '', localtimestamp, localtimestamp)
            returning id
            """
        ).param(program["id"].asLong()).query(Long::class.java).single()
        jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', 't', 'EVENT', 'CONFIRMED', '', 0, localtimestamp, localtimestamp)")
            .param(activity).update()
        val edited = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"nameEn":"Workshop","descriptionEn":" Details "}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf("영문 활동", "Workshop", "설명", "Details"), listOf("name", "nameEn", "description", "descriptionEn").map { edited[it].asString() })
        // 보낸 필드만 운영자 지정 필드(admin-v1.md "운영자 지정 필드")에 남는다
        assertEquals("[\"nameEn\",\"descriptionEn\"]", edited["curatedFields"].toString())
        // 공식 안내·신청 링크(2026-10-19): https만 받고, 신청 링크는 빈 문자열이면 지우며, 둘 다 운영자 지정 필드에 남는다
        val linked = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"officialUrl":" https://example.org/notice ","applicationUrl":"https://example.org/apply"}""") }
            .andExpect { status { isOk() } })
        assertEquals("https://example.org/notice", linked["officialUrl"].asText())
        assertEquals("https://example.org/apply", linked["applicationUrl"].asText())
        assertEquals("[\"nameEn\",\"descriptionEn\",\"officialUrl\",\"applicationUrl\"]", linked["curatedFields"].toString())
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"officialUrl":"http://example.org/notice"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"officialUrl":""}""") }.andExpect { status { isBadRequest() } }
        assertEquals(true, read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"applicationUrl":""}""") }.andExpect { status { isOk() } })["applicationUrl"].isNull)
        // 활동 종류(native-v1.md "활동 종류", 2026-10-19): 코드로 바꾸면 운영자 지정 필드에 남고, 모르는 코드는 400, null은 미분류
        val typed = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"category":"bootcamp"}""") }.andExpect { status { isOk() } })
        assertEquals("BOOTCAMP", typed["category"].asString())
        assertEquals("[\"nameEn\",\"descriptionEn\",\"officialUrl\",\"applicationUrl\",\"category\"]", typed["curatedFields"].toString())
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"category":"NOPE"}""") }.andExpect { status { isBadRequest() } }
        assertEquals(true, read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"category":null}""") }.andExpect { status { isOk() } })["category"].isNull)
        // 프로그램 기본 종류(admin-v1.md "프로그램 기본 종류"): 지정하면 응답에 보이고, 일괄 적용은 종류가 비었거나 운영자가 지정하지 않은 활동만 바꾼다
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"category":"club"}""") }.andExpect { status { isOk() } }
        val plain = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, created_at, updated_at)
            values (?, '종류 없는 활동', '', 'REGISTRATION', 'UNKNOWN', 'https://example.org', 'STALE', '', localtimestamp, localtimestamp)
            returning id
            """
        ).param(program["id"].asLong()).query(Long::class.java).single()
        jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', 't', 'EVENT', 'CONFIRMED', '', 0, localtimestamp, localtimestamp)")
            .param(plain).update()
        val bulk = read(mvc.patch("/v1/admin/programs/${program["id"].asLong()}") { asAdmin(); patchBody("""{"category":"study","applyCategoryToActivities":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf("STUDY", 1), listOf(bulk["category"].asString(), bulk["appliedActivityCount"].asInt()))
        assertEquals(listOf("CLUB", "STUDY"), jdbc.sql("select category from activities where id in (?, ?) order by id").params(activity, plain).query(String::class.java).list())
        mvc.patch("/v1/admin/programs/${program["id"].asLong()}") { asAdmin(); patchBody("""{"category":"NOPE"}""") }.andExpect { status { isBadRequest() } }
        val unset = read(mvc.patch("/v1/admin/programs/${program["id"].asLong()}") { asAdmin(); patchBody("""{"category":null}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(true, true), listOf(unset["category"].isNull, unset["appliedActivityCount"].isNull))
        // 태그(native-v1.md "태그"): 사전 CRUD(띄어쓰기만 다른 이름은 기존 태그로 합침), 프로그램 태그 일괄 적용은 운영자가 태그를 지정한 활동을 건너뛴다
        val startup = read(mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":" 창 업 "}""") }.andExpect { status { isCreated() } })
        assertEquals("startup", startup["nameEn"].asString())
        val created = read(mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"테스트 태그"}""") }.andExpect { status { isCreated() } })
        assertEquals(true, created["nameEn"].isNull)
        assertEquals("Test Tag", read(mvc.patch("/v1/admin/tags/${created["id"].asLong()}") { asAdmin(); patchBody("""{"nameEn":"Test Tag"}""") }.andExpect { status { isOk() } })["nameEn"].asString())
        mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":" "}""") }.andExpect { status { isBadRequest() } }
        // 종류·하위 분야(native-v1.md "분야·성격 태그와 아이콘"): 기본 TRAIT. 상위 분야는 FIELD 최상위만, 한 단계
        assertEquals(listOf("TRAIT", "true"), listOf(created["kind"].asString(), created["parentId"].isNull.toString()))
        val top = read(mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"시험 상위 분야","kind":"FIELD"}""") }.andExpect { status { isCreated() } })["id"].asLong()
        val child = read(mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"시험 하위 분야","kind":"FIELD","parentId":$top}""") }.andExpect { status { isCreated() } })
        assertEquals(top, child["parentId"].asLong())
        mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"시험 성격","parentId":$top}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"시험 손자 분야","kind":"FIELD","parentId":${child["id"].asLong()}}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/tags") { asAdmin(); body("""{"name":"시험 종류","kind":"NOPE"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/tags/$top") { asAdmin(); patchBody("""{"kind":"TRAIT"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/tags/$top") { asAdmin(); patchBody("""{"parentId":${child["id"].asLong()}}""") }.andExpect { status { isBadRequest() } }
        assertEquals(true, read(mvc.patch("/v1/admin/tags/${child["id"].asLong()}") { asAdmin(); patchBody("""{"parentId":null}""") }.andExpect { status { isOk() } })["parentId"].isNull)
        assertEquals("FIELD", read(mvc.get("/v1/admin/tags") { asAdmin() }.andExpect { status { isOk() } })["items"].toList().single { it["id"].asLong() == top }["kind"].asString())
        jdbc.sql("delete from tags where id in (?, ?)").params(top, child["id"].asLong()).update()
        val keptId = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, curated_fields, created_at, updated_at)
            values (?, '태그 지정된 활동', '', 'REGISTRATION', 'UNKNOWN', 'https://example.org', 'STALE', '', '["tags"]'::jsonb, localtimestamp, localtimestamp)
            returning id
            """
        ).param(program["id"].asLong()).query(Long::class.java).single()
        jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', 't', 'EVENT', 'CONFIRMED', '', 0, localtimestamp, localtimestamp)")
            .param(keptId).update()
        val taggedProgram = read(
            mvc.patch("/v1/admin/programs/${program["id"].asLong()}") { asAdmin(); patchBody("""{"tagIds":[${created["id"].asLong()},${startup["id"].asLong()}],"applyTagsToActivities":true}""") }
                .andExpect { status { isOk() } }
        )
        // 프로그램 태그는 보낸 순서를 position으로 남기고 그 순서로 돌려준다(분야 태그 순서)
        assertEquals(listOf("테스트 태그", "창업"), taggedProgram["tags"].let(::tagNames))
        assertEquals(listOf(0, 1), jdbc.sql("select position from program_tags where program_id = ? order by tag_id = ? desc").params(program["id"].asLong(), created["id"].asLong()).query(Int::class.java).list())
        // plain·activity(운영자 미지정)에는 더해지고 kept(운영자 지정)는 그대로
        assertEquals(2, taggedProgram["appliedTagActivityCount"].asInt())
        assertEquals(listOf(2, 0), listOf(plain, keptId).map { jdbc.sql("select count(*) from activity_tags where activity_id = ?").param(it).query(Int::class.java).single() })
        // 활동 태그 PATCH는 통째로 바꾸고 운영자 지정 tags에 남긴다. 없는 id는 400
        val taggedActivity = read(mvc.patch("/v1/admin/activities/$plain") { asAdmin(); patchBody("""{"tagIds":[${created["id"].asLong()}]}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("테스트 태그"), taggedActivity["tags"].let(::tagNames))
        assertEquals(true, taggedActivity["curatedFields"].toList().any { it.asString() == "tags" })
        // 운영자 지정 활동 태그는 연관도가 없다
        jdbc.sql("update activity_tags set relevance = 50 where activity_id = ?").param(plain).update()
        mvc.patch("/v1/admin/activities/$plain") { asAdmin(); patchBody("""{"tagIds":[${created["id"].asLong()}]}""") }.andExpect { status { isOk() } }
        assertEquals(listOf<Int?>(null), jdbc.sql("select relevance from activity_tags where activity_id = ?").param(plain).query { rs, _ -> rs.getObject(1) as Int? }.list())
        mvc.patch("/v1/admin/activities/$plain") { asAdmin(); patchBody("""{"tagIds":[999999]}""") }.andExpect { status { isBadRequest() } }
        // 운영자 생성 활동은 tagIds를 생략하면 프로그램 태그를 기본으로 받는다
        val createdActivity = read(
            mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":${program["id"].asLong()},"name":"태그 기본값 활동","description":"d","officialUrl":"https://example.org/x","activitySchedule":{"dateLabel":"2027"}}""") }
                .andExpect { status { isCreated() } }
        )
        assertEquals(listOf("창업", "테스트 태그"), createdActivity["tags"].let(::tagNames))
        assertEquals(false, createdActivity["curatedFields"].toList().any { it.asString() == "tags" })
        jdbc.sql("delete from activities where id in (?, ?)").params(keptId, createdActivity["id"].asLong()).update()
        // 삭제는 연결도 지운다
        mvc.delete("/v1/admin/tags/${created["id"].asLong()}") { asAdmin() }.andExpect { status { isNoContent() } }
        assertEquals(0, jdbc.sql("select count(*) from activity_tags where tag_id = ?").param(created["id"].asLong()).query(Int::class.java).single())
        mvc.delete("/v1/admin/tags/999999") { asAdmin() }.andExpect { status { isNotFound() } }
        jdbc.sql("delete from program_tags where program_id = ?").param(program["id"].asLong()).update()
        jdbc.sql("delete from activity_tags where activity_id = ?").param(activity).update()
        jdbc.sql("delete from activities where id = ?").param(plain).update()
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"name":" "}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/999999") { asAdmin(); patchBody("""{"nameEn":"x"}""") }.andExpect { status { isNotFound() } }

        // 대상자 조건(native-v1.md "대상자 조건"): 어휘 밖 field는 400, 영문만 보내도 한글이 채워지고 검색용 표가 함께 쓰인다. JSON null이면 지운다
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"eligibility":{"op":"all","items":[{"field":"키","from":170}]}}""") }.andExpect { status { isBadRequest() } }
        val withEligibility = read(
            mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"eligibility":{"op":"all","items":[{"fieldEn":"age","from":19,"to":34},{"fieldEn":"employment","valuesEn":["unemployed"]}]}}""") }
                .andExpect { status { isOk() } }
        )["eligibility"]
        assertEquals(listOf("나이", "취업상태"), listOf(withEligibility["items"][0]["field"].asString(), withEligibility["items"][1]["field"].asString()))
        assertEquals("미취업", withEligibility["items"][1]["values"][0].asString())
        assertEquals(2, jdbc.sql("select count(*) from activity_eligibility_terms where activity_id = ?").param(activity).query(Int::class.java).single())
        // 지운 것도 운영자가 확인한 것이다(제한 없음, native-v1.md "추천 설정 퍼널·지원 자격 정보·추천")
        jdbc.sql("update activities set eligibility_checked = false where id = ?").param(activity).update()
        val clearedEligibility = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"eligibility":null}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(true, true), listOf(clearedEligibility["eligibility"].isNull, clearedEligibility["eligibilityChecked"].asBoolean()))
        assertEquals(0, jdbc.sql("select count(*) from activity_eligibility_terms where activity_id = ?").param(activity).query(Int::class.java).single())
        // 이전 기수에서 가져온 표시(admin-v1.md "이전 기수 정보 자동완성")는 운영자가 그 필드를 고치면 빠진다
        jdbc.sql("update activities set inherited_fields = '[\"eligibility\",\"audience\"]'::jsonb where id = ?").param(activity).update()
        val reset = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"eligibility":{"op":"all","items":[{"fieldEn":"age","from":20}]}}""") }.andExpect { status { isOk() } })
        assertEquals("[\"audience\"]", reset["inheritedFields"].toString())
        // 대상자 조건을 비우거나 다시 저장해도 운영자 지정 표시는 한 번만 남는다
        assertEquals("[\"nameEn\",\"descriptionEn\",\"officialUrl\",\"applicationUrl\",\"category\",\"eligibility\"]", reset["curatedFields"].toString())

        // 일정 직접 지정(admin-v1.md): 날짜만 주면 종일 일정(끝 날짜 포함 → exclusive는 하루 뒤), curated가 켜진다. 모집 기간은 없던 행을 만든다. 섞으면 400, null이면 지정 해제
        val dated = read(
            mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-05-26","endDate":"2027-02-06"},"application":{"startAt":"2026-05-26T00:00:00+09:00","endAt":"2026-06-09T11:00:00+09:00"}}""") }
                .andExpect { status { isOk() } }
        )
        assertEquals(listOf("2026-05-26", "2027-02-07", true), listOf(dated["activitySchedule"]["startDate"].asString(), dated["activitySchedule"]["endDateExclusive"].asString(), dated["activitySchedule"]["curated"].asBoolean()))
        assertEquals(listOf("Asia/Seoul", true), listOf(dated["application"]["timeZone"].asString(), dated["application"]["curated"].asBoolean()))
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-05-26","endAt":"2026-06-09T11:00:00+09:00"}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-05-26","endDate":"2026-05-01"}}""") }.andExpect { status { isBadRequest() } }
        // 활동 기간도 시각 일정이 된다(V39, 2026-10-19 밤: 전에는 DB 체크로 500이 났다)
        val timedActivity = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"startAt":"2026-10-28T10:00:00+09:00","endAt":"2026-10-28T18:00:00+09:00"}}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("2026-10-28T10:00:00+09:00", "2026-10-28T18:00:00+09:00", true), listOf(timedActivity["activitySchedule"]["startAt"].asString(), timedActivity["activitySchedule"]["endAt"].asString(), timedActivity["activitySchedule"]["startDate"].isNull))
        // 마감일만 지정(admin-v1.md "마감일만 지정"·"사유"): endDate만 오면 그날 23:59(Asia/Seoul)까지의 시점 일정, 사유는 curatedNote로 남는다
        val deadline = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"application":{"endDate":"2026-06-09","note":"접수 마감"}}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(true, "2026-06-09T23:59:00+09:00", "접수 마감", true), listOf(deadline["application"]["startAt"].isNull, deadline["application"]["endAt"].asString(), deadline["application"]["curatedNote"].asString(), deadline["application"]["curated"].asBoolean()))
        val startOnly = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"application":{"startDate":"2026-05-26"}}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("2026-05-26T00:00:00+09:00", true, true), listOf(startOnly["application"]["startAt"].asString(), startOnly["application"]["endAt"].isNull, startOnly["application"]["curatedNote"].isNull))
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"application":{"endDate":"2026-06-09","note":"${"가".repeat(301)}"}}""") }.andExpect { status { isBadRequest() } }
        val cleared = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"application":null}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(true, false, true), listOf(cleared["application"]["startAt"].isNull, cleared["application"]["curated"].asBoolean(), cleared["application"]["curatedNote"].isNull))
    }

    @Test
    fun `활동 장소는 활동 기간 행에 저장되어 place로 보이고, 잘못된 좌표는 400, null은 네 열을 모두 지운다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val created = read(
            mvc.post("/v1/admin/activities") {
                asAdmin()
                body("""{"programId":$programId,"name":"장소 있는 활동","description":"d","officialUrl":"https://example.org/x","activitySchedule":{"dateLabel":"2027 상반기"},
                       "place":{"name":"코엑스 그랜드볼룸","address":"서울 강남구 영동대로 513","lat":37.5116828,"lng":127.0594}}""")
            }.andExpect { status { isCreated() } }
        )
        val id = created["id"].asLong()
        assertEquals("""{"name":"코엑스 그랜드볼룸","address":"서울 강남구 영동대로 513","lat":37.5116828,"lng":127.0594}""", created["place"].toString())
        assertEquals(listOf("name", "description", "officialUrl", "place"), created["curatedFields"].toList().map { it.asString() })
        // 활동 기간(slot ACTIVITY) 행의 네 열에 쓰인다
        assertEquals(
            "코엑스 그랜드볼룸|서울 강남구 영동대로 513|37.5116828|127.0594",
            jdbc.sql("select address_line1 || '|' || address_line2 || '|' || lat || '|' || lng from schedules where activity_id = ? and slot = 'ACTIVITY'").param(id).query(String::class.java).single(),
        )
        // 수집된 활동(활동 기간이 운영자 지정이 아닌 것)에 장소를 넣어도 시간 지정(schedules.curated)은 켜지지 않고 curated_fields에 place만 남는다
        val collected = jdbc.sql("select a.id from activities a join schedules s on s.activity_id = a.id and s.slot = 'ACTIVITY' where s.curated = false and a.id <> ? order by a.id limit 1")
            .param(id).query(Long::class.java).single()
        val previousFields = jdbc.sql("select curated_fields::text from activities where id = ?").param(collected).query(String::class.java).single()
        val collectedPlaced = read(mvc.patch("/v1/admin/activities/$collected") { asAdmin(); patchBody("""{"place":{"name":"코엑스","address":"서울 강남구 영동대로 513","lat":37.5116828,"lng":127.0594}}""") }.andExpect { status { isOk() } })
        assertEquals("코엑스", collectedPlaced["place"]["name"].asString())
        assertEquals(true, collectedPlaced["curatedFields"].toList().any { it.asString() == "place" })
        assertEquals("false", jdbc.sql("select curated::text from schedules where activity_id = ? and slot = 'ACTIVITY'").param(collected).query(String::class.java).single())
        jdbc.sql("update schedules set address_line1 = null, address_line2 = null, lat = null, lng = null where activity_id = ? and slot = 'ACTIVITY'").param(collected).update()
        jdbc.sql("update activities set curated_fields = ?::jsonb where id = ?").params(previousFields, collected).update()

        // PATCH로 바꾸면 그 값으로, 주소는 비워도 된다(빈 문자열로 저장)
        val moved = read(mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":"온라인 ZOOM","lat":37.0,"lng":127.0}}""") }.andExpect { status { isOk() } })
        assertEquals("""{"name":"온라인 ZOOM","address":"","lat":37.0,"lng":127.0}""", moved["place"].toString())
        assertEquals(1, moved["curatedFields"].toList().count { it.asString() == "place" })
        // 위도 범위 밖, 좌표 없음, 이름 없음, 이름 201자, 주소 301자는 400
        mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":"x","address":"","lat":91,"lng":127}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":"x","address":"","lat":37,"lng":"127"}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":" ","address":"","lat":37,"lng":127}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":"${"가".repeat(201)}","lat":37,"lng":127}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":{"name":"x","address":"${"가".repeat(301)}","lat":37,"lng":127}}""") }.andExpect { status { isBadRequest() } }
        // 생략하면 그대로, null이면 네 열을 모두 지운다(CHECK: 모두 null이거나 모두 값)
        assertEquals("온라인 ZOOM", read(mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"nameEn":"Placed"}""") }.andExpect { status { isOk() } })["place"]["name"].asString())
        val cleared = read(mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"place":null}""") }.andExpect { status { isOk() } })
        assertEquals(true, cleared["place"].isNull)
        assertEquals(4, jdbc.sql("select (address_line1 is null)::int + (address_line2 is null)::int + (lat is null)::int + (lng is null)::int from schedules where activity_id = ? and slot = 'ACTIVITY'").param(id).query(Int::class.java).single())
        jdbc.sql("delete from activities where id = ?").param(id).update()
    }

    @Test
    fun `장소 검색은 제공자 결과를 items로 주고, 검색어가 2자 미만이거나 100자 초과면 400`() {
        val found = read(mvc.get("/v1/admin/geocode?q=코엑스") { asAdmin() }.andExpect { status { isOk() } })["items"]
        assertEquals(2, found.size())
        assertEquals("""{"name":"코엑스","address":"서울 강남구 영동대로 513","lat":37.5116828,"lng":127.0594,"source":"fake"}""", found[0].toString())
        mvc.get("/v1/admin/geocode?q=코") { asAdmin() }.andExpect { status { isBadRequest() } }
        mvc.get("/v1/admin/geocode?q=${"가".repeat(101)}") { asAdmin() }.andExpect { status { isBadRequest() } }
        mvc.get("/v1/admin/geocode") { asAdmin() }.andExpect { status { isBadRequest() } }
        mvc.get("/v1/admin/geocode?q=코엑스").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `조심 목록은 프로그램별로 이름을 모으고, 중복 지정은 활동을 지우면서 항목을 만든다`() {
        val program = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        fun activity(name: String) = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, source_id, created_at, updated_at)
            values (?, ?, '설명', 'REGISTRATION', 'UNKNOWN', 'https://example.org', 'STALE', '', gen_random_uuid(), localtimestamp, localtimestamp)
            returning id
            """
        ).params(program, name).query(Long::class.java).single().also { id ->
            jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', ?, 'EVENT', 'CONFIRMED', '', 0, localtimestamp, localtimestamp)")
                .params(id, name).update()
        }
        val canonical = activity("우아한 테크코스 8기")
        val duplicate = activity("우아한테크코스 8기")

        jdbc.sql("delete from collection_feedback").update()
        val created = read(mvc.post("/v1/admin/programs/$program/watchlist") { asAdmin(); body("""{"name":"다른 과정 수료식","kind":"exclude","reason":"다른 프로그램"}""") }.andExpect { status { isCreated() } })
        assertEquals(listOf("exclude", "다른과정수료식"), listOf(created["kind"].asString(), created["normalizedName"].asString()))
        mvc.post("/v1/admin/programs/$program/watchlist") { asAdmin(); body("""{"name":"다른 과정  수료식","kind":"exclude"}""") }.andExpect { status { isConflict() } }
        mvc.post("/v1/admin/programs/$program/watchlist") { asAdmin(); body("""{"name":"x","kind":"alias"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/programs/$program/watchlist") { asAdmin(); body("""{"name":"x","kind":"merge"}""") }.andExpect { status { isBadRequest() } }

        val duplicateSource = jdbc.sql("select source_id::text from activities where id = ?").param(duplicate).query(String::class.java).single()
        mvc.post("/v1/admin/activities/$duplicate/duplicate") { asAdmin(); body("""{"kind":"alias","canonicalActivityId":$duplicate}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities/$duplicate/duplicate") { asAdmin(); body("""{"kind":"alias","canonicalActivityId":$canonical,"reason":"띄어쓰기만 다름"}""") }.andExpect { status { isNoContent() } }
        assertEquals(0, jdbc.sql("select count(*) from activities where id = ?").param(duplicate).query(Int::class.java).single())
        assertEquals(1, jdbc.sql("select count(*) from dismissed_activity_sources where source_id = ?::uuid").param(duplicateSource).query(Int::class.java).single())

        val items = read(mvc.get("/v1/admin/programs/$program/watchlist") { asAdmin() }.andExpect { status { isOk() } })["items"]
        assertEquals(2, items.size())
        val alias = items.first { it["kind"].asString() == "alias" }
        assertEquals(listOf("우아한테크코스 8기", canonical, "우아한 테크코스 8기"), listOf(alias["name"].asString(), alias["canonicalActivityId"].asLong(), alias["canonicalActivityName"].asString()))
        // 수집 피드백(V43): 조심 목록 항목마다 한 행(사유·정본 이름 포함). 항목을 지우면 그 행도 지운다
        fun feedbackRows() = jdbc.sql("select kind || '|' || source_text || '|' || coalesce(reason, '') || '|' || coalesce(corrected_value ->> 'canonicalActivityName', '') from collection_feedback order by id").query(String::class.java).list()
        assertEquals(listOf("WATCH_EXCLUDE|다른 과정 수료식|다른 프로그램|", "WATCH_ALIAS|우아한테크코스 8기|띄어쓰기만 다름|우아한 테크코스 8기"), feedbackRows())
        mvc.delete("/v1/admin/watchlist/${alias["id"].asLong()}") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/watchlist/${alias["id"].asLong()}") { asAdmin() }.andExpect { status { isNotFound() } }
        assertEquals(listOf("WATCH_EXCLUDE|다른 과정 수료식|다른 프로그램|"), feedbackRows())
    }

    @Test
    fun `운영자가 수집한 활동을 고치거나 지우면 수집 피드백으로 남고, 직접 만든 활동은 남지 않는다`() {
        val program = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        fun activity(name: String, collected: Boolean) = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, source_id, created_at, updated_at)
            values (?, ?, '설명', 'REGISTRATION', 'UNKNOWN', 'https://letswift.kr/2026/', 'STALE', '', ${if (collected) "gen_random_uuid()" else "null"}, localtimestamp, localtimestamp)
            returning id
            """
        ).params(program, name).query(Long::class.java).single().also { id ->
            jdbc.sql(
                """
                insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at)
                values (?, 'ACTIVITY', '활동', 'EVENT', 'CONFIRMED', 'ALL_DAY', date '2026-11-29', date '2026-11-30', '11월 29일(토)', 0, localtimestamp, localtimestamp),
                       (?, 'APPLICATION', '모집', 'DEADLINE', 'CONFIRMED', 'ALL_DAY', date '2026-11-01', date '2026-11-11', '11월 1일 ~ 11월 10일', 0, localtimestamp, localtimestamp)
                """
            ).params(id, id).update()
        }
        jdbc.sql("delete from collection_feedback").update()
        fun rows() = jdbc.sql("select kind || '|' || source_text || '|' || corrected_value::text || '|' || coalesce(reason, '') from collection_feedback order by id").query(String::class.java).list()
        val collected = activity("Let'Swift 2026 수집", collected = true)
        val manual = activity("Let'Swift 2026 직접", collected = false)

        mvc.patch("/v1/admin/activities/$manual") { asAdmin(); patchBody("""{"name":"Let'Swift 2026 직접 등록","category":"MEETUP"}""") }.andExpect { status { isOk() } }
        assertEquals(emptyList(), rows())

        // 필드: 수집 값 → 운영자 값
        mvc.patch("/v1/admin/activities/$collected") { asAdmin(); patchBody("""{"name":"Let'Swift 2026","category":"CONFERENCE"}""") }.andExpect { status { isOk() } }
        assertEquals(
            listOf(
                "FIELD_CURATED|Let'Swift 2026 수집|{\"to\": \"Let'Swift 2026\", \"from\": \"Let'Swift 2026 수집\", \"field\": \"name\"}|",
                "FIELD_CURATED|Let'Swift 2026 수집|{\"to\": \"CONFERENCE\", \"from\": null, \"field\": \"category\"}|",
            ),
            rows(),
        )
        // 모집 ↔ 활동 바꾸기는 PERIOD_SWAP 한 행
        jdbc.sql("delete from collection_feedback").update()
        mvc.patch("/v1/admin/activities/$collected") { asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-11-01","endDate":"2026-11-10","note":"기간이 바뀌어 들어옴"},"application":{"startDate":"2026-11-29","endDate":"2026-11-29"}}""") }.andExpect { status { isOk() } }
        assertEquals(
            listOf("PERIOD_SWAP|모집: 11월 1일 ~ 11월 10일 / 활동: 11월 29일(토)|{\"activity\": {\"endDate\": \"2026-11-10\", \"startDate\": \"2026-11-01\"}, \"application\": {\"endDate\": \"2026-11-29\", \"startDate\": \"2026-11-29\"}}|기간이 바뀌어 들어옴"),
            rows(),
        )
        // 기간 하나만 고치면 그 원문 문구의 해석(DATE_CURATED)
        jdbc.sql("delete from collection_feedback").update()
        mvc.patch("/v1/admin/activities/$collected") { asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-11-28","endDate":"2026-11-29","note":"이틀 행사"}}""") }.andExpect { status { isOk() } }
        assertEquals(listOf("DATE_CURATED|11월 29일(토)|{\"slot\": \"ACTIVITY\", \"endDate\": \"2026-11-29\", \"startDate\": \"2026-11-28\"}|이틀 행사"), rows())
        // 지우면 이름이 DRAFT_DISMISSED로 남는다(직접 만든 활동은 남지 않는다)
        jdbc.sql("delete from collection_feedback").update()
        mvc.delete("/v1/admin/activities/$collected") { asAdmin() }.andExpect { status { is2xxSuccessful() } }
        mvc.delete("/v1/admin/activities/$manual") { asAdmin() }.andExpect { status { is2xxSuccessful() } }
        assertEquals(listOf("DRAFT_DISMISSED|Let'Swift 2026|{}|"), rows())
        assertEquals(listOf("letswift.kr"), jdbc.sql("select host from collection_feedback").query(String::class.java).list())
        jdbc.sql("delete from collection_feedback").update()
    }

    @Test
    fun `수집 설정은 https 주소와 호스트 이름만 받는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val url = "/v1/admin/programs/$programId/collection-settings"
        // http는 그 호스트를 허용 호스트에 적었을 때만(2026-10-24)
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"http://letswift.kr","intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"http://letswift.kr","allowedHosts":["other.kr"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"http://letswift.kr","allowedHosts":["letswift.kr"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isOk() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["https://letswift.kr/2026"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["letswift.kr"],"intervalDays":0,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        val saved = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://letswift.kr/2026/","allowedHosts":["letswift.kr"],"intervalDays":3,"enabled":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(3, saved["intervalDays"].asInt())
        // 브라우저 렌더링(admin-v1.md, 2026-10-19): 기본 false, 켜서 저장하면 응답과 다시 읽기에 남는다
        assertEquals(false, saved["renderPages"].asBoolean())
        val rendered = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://letswift.kr/2026/","allowedHosts":["letswift.kr"],"intervalDays":3,"enabled":true,"renderPages":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(true, rendered["renderPages"].asBoolean())
        assertEquals(true, read(mvc.get(url) { asAdmin() }.andExpect { status { isOk() } })["renderPages"].asBoolean())
        mvc.get("/v1/admin/programs/999999/collection-settings") { asAdmin() }.andExpect { status { isNotFound() } }
        // 주소 토큰(admin-v1.md, 2026-10-19): 모르는 토큰은 400, {yy}는 올해·내년으로 펼쳐 previewUrls에, 기수를 못 읽으면 previousRound는 null
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://toss.im/slash-{season}","allowedHosts":["toss.im"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        val tokened = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://toss.im/slash-{yy}","allowedHosts":["toss.im"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isOk() } })
        val yy = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).year % 100
        assertEquals("[\"https://toss.im/slash-${yy + 1}\",\"https://toss.im/slash-$yy\"]", tokened["previewUrls"].toString())
        assertEquals(true, tokened["previousRound"].isNull)
        assertEquals("[]", tokened["lastFetchedUrls"].toString())
        // {round}: 가장 최근 활동 이름의 "N기"를 읽어 다음 기수·현재 기수로 펼친다. 마지막 성공 수집이 받은 페이지는 lastFetchedUrls로 보인다
        val activityName = jdbc.sql("select name from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query(String::class.java).single()
        jdbc.sql("update activities set name = 'Let''Swift 12기' where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
        jdbc.sql("insert into collection_runs (program_id, run_date, official_only, started_at, outcome, fetched_urls) values (?, date '2026-10-18', true, now(), 'SUCCEEDED', array['https://letswift.kr/2026/'])")
            .param(programId).update()
        try {
            val rounded = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://letswift.kr/{round}/","allowedHosts":["letswift.kr"],"intervalDays":1,"enabled":true}""") }
                .andExpect { status { isOk() } })
            assertEquals(12, rounded["previousRound"].asInt())
            assertEquals("[\"https://letswift.kr/13/\",\"https://letswift.kr/12/\"]", rounded["previewUrls"].toString())
            assertEquals("[\"https://letswift.kr/2026/\"]", rounded["lastFetchedUrls"].toString())
        } finally {
            jdbc.sql("update activities set name = ? where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").param(activityName).update()
            jdbc.sql("delete from collection_runs where program_id = ?").param(programId).update()
        }
    }

    @Test
    fun `운영자가 활동을 직접 만들면 초안으로 목록에 보이고, 같은 이름·http 주소·기간 없음은 막는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val created = read(
            mvc.post("/v1/admin/activities") {
                asAdmin()
                body(
                    """{"programId":$programId,"name":"운영자 활동 1기","description":"직접 등록","officialUrl":"https://example.org/notice","applicationUrl":"https://example.org/apply",
                       "participationType":"SELECTION","category":"CLUB","activitySchedule":{"startDate":"2026-12-01","endDate":"2026-12-03"},
                       "application":{"startAt":"2026-11-01T10:00:00+09:00","endAt":"2026-11-10T23:59:00+09:00"},
                       "eligibility":{"op":"all","items":[{"fieldEn":"age","from":19,"to":34}]},"audience":"19~34세","location":"서울","cost":"무료"}"""
                )
            }.andExpect { status { isCreated() } }
        )
        assertEquals("DRAFT", created["publicationStatus"].asString())
        assertEquals(true, created["officialSource"].asBoolean())
        assertEquals("CLUB", created["category"].asString())
        assertEquals("[\"name\",\"description\",\"officialUrl\",\"applicationUrl\",\"participationType\",\"category\",\"audience\",\"location\",\"cost\",\"eligibility\"]", created["curatedFields"].toString())
        assertEquals(true, created["activitySchedule"]["curated"].asBoolean())
        assertEquals("2026-12-01", created["activitySchedule"]["startDate"].asString())
        assertEquals(true, created["application"]["curated"].asBoolean())
        assertEquals("19", created["eligibility"]["items"][0]["from"].asString())
        // 유사도 검색이라 비슷한 이름이 함께 올 수 있다. 만든 활동이 초안 목록에 있는지만 본다
        val listed = read(mvc.get("/v1/admin/activities?publicationStatus=DRAFT&programId=$programId&q=운영자 활동 1기") { asAdmin() })["items"]
        assertEquals(true, (0 until listed.size()).any { listed[it]["id"].asLong() == created["id"].asLong() })
        assertEquals("운영자 등록|", jdbc.sql("select source_note || '|' || coalesce(source_id::text, '') from activities where id = ?").param(created["id"].asLong()).query(String::class.java).single())
        // 같은 프로그램에 띄어쓰기만 다른 이름, http 주소, 기간 없음, 없는 프로그램은 400
        mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":$programId,"name":"운영자활동 1기","description":"","officialUrl":"https://example.org/x","activitySchedule":{"dateLabel":"2027"}}""") }
            .andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":$programId,"name":"다른 활동","description":"","officialUrl":"http://example.org/x","activitySchedule":{"dateLabel":"2027"}}""") }
            .andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":$programId,"name":"기간 없는 활동","description":"","officialUrl":"https://example.org/x"}""") }
            .andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":999999,"name":"없는 프로그램","description":"","officialUrl":"https://example.org/x","activitySchedule":{"dateLabel":"2027"}}""") }
            .andExpect { status { isBadRequest() } }
        // 원문 문구만으로도 만들 수 있다
        val labelled = read(mvc.post("/v1/admin/activities") { asAdmin(); body("""{"programId":$programId,"name":"문구만 활동","description":"","officialUrl":"https://example.org/y","activitySchedule":{"dateLabel":"2027년 봄"}}""") }
            .andExpect { status { isCreated() } })
        assertEquals("2027년 봄", labelled["activitySchedule"]["dateLabel"].asString())
        assertEquals("REGISTRATION", jdbc.sql("select participation_type from activities where id = ?").param(labelled["id"].asLong()).query(String::class.java).single())
    }

    @Test
    fun `세부 일정은 만들고 고치고 지울 수 있고, 슬롯 일정은 막으며, 지운 일정은 표시만 남는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val created = read(
            mvc.post("/v1/admin/activities") {
                asAdmin()
                body("""{"programId":$programId,"name":"일정 편집 활동","description":"세부 일정 테스트","officialUrl":"https://example.org/schedules","activitySchedule":{"startDate":"2026-12-01","endDate":"2026-12-03"}}""")
            }.andExpect { status { isCreated() } }
        )
        val activity = created["id"].asLong()
        val slotSchedule = created["activitySchedule"]["id"].asLong()
        // 만들기(admin-v1.md "세부 일정 편집기"): 슬롯 없는 일정이 curated로 생기고 사유가 남는다
        val interview = read(
            mvc.post("/v1/admin/activities/$activity/schedules") {
                asAdmin(); body("""{"title":"1차 면접","kind":"INTERVIEW","startAt":"2026-11-15T10:00:00+09:00","endAt":"2026-11-15T12:00:00+09:00","note":"공지 PDF 3쪽"}""")
            }.andExpect { status { isCreated() } }
        )
        assertEquals(listOf("1차 면접", "INTERVIEW", "CONFIRMED", "2026-11-15T10:00:00+09:00", true, "공지 PDF 3쪽"),
            listOf(interview["title"].asString(), interview["kind"].asString(), interview["status"].asString(), interview["startAt"].asString(), interview["curated"].asBoolean(), interview["curatedNote"].asString()))
        val scheduleId = interview["id"].asLong()
        assertEquals(1, jdbc.sql("select count(*) from schedules where id = ? and slot is null and curated and not curated_deleted and sequence = 0").param(scheduleId).query(Int::class.java).single())
        // 목록 응답의 schedules에 세부 일정만 보인다
        fun listedSchedules(): List<Long> {
            val items = read(mvc.get("/v1/admin/activities?publicationStatus=DRAFT&programId=$programId&q=일정 편집 활동") { asAdmin() })["items"]
            val row = (0 until items.size()).map { items[it] }.single { it["id"].asLong() == activity }
            return (0 until row["schedules"].size()).map { row["schedules"][it]["id"].asLong() }
        }
        assertEquals(listOf(scheduleId), listedSchedules())
        // 부분 수정: 제목만 보내면 시각·사유는 그대로, sequence는 1 오른다
        val renamed = read(mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"title":"1차 면접(온라인)"}""") }.andExpect { status { isOk() } })
        assertEquals(listOf("1차 면접(온라인)", "2026-11-15T10:00:00+09:00", "공지 PDF 3쪽"), listOf(renamed["title"].asString(), renamed["startAt"].asString(), renamed["curatedNote"].asString()))
        assertEquals(1, jdbc.sql("select sequence from schedules where id = ?").param(scheduleId).query(Int::class.java).single())
        // 날짜만 보내면 종일 일정으로 바뀌고 시각은 비워진다
        val dated = read(mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"startDate":"2026-11-16","endDate":"2026-11-16"}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(true, "2026-11-16", "2026-11-17"), listOf(dated["startAt"].isNull, dated["startDate"].asString(), dated["endDateExclusive"].asString()))
        // 섞으면 400, 끝이 앞서면 400, 제목을 비우면 400, 시각도 날짜도 문구도 없으면 400
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"startDate":"2026-11-16","endAt":"2026-11-16T12:00:00+09:00"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"startAt":"2026-11-16T12:00:00+09:00","endAt":"2026-11-16T10:00:00+09:00"}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"title":"  "}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"문구도 없는 일정","kind":"EVENT"}""") }.andExpect { status { isBadRequest() } }
        mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"종류 없는 일정","dateLabel":"추후 공지"}""") }.andExpect { status { isBadRequest() } }
        // 문구만으로는 만들 수 있다
        val labelled = read(mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"결과 발표","kind":"ANNOUNCEMENT","dateLabel":"12월 중"}""") }.andExpect { status { isCreated() } })
        assertEquals(listOf(true, "12월 중"), listOf(labelled["startAt"].isNull, labelled["dateLabel"].asString()))
        // 겹침 확인 시간(native-v1.md "겹침 확인 시간"): 세부 일정과 모집·활동 기간 모두 PATCH의 overlapCheck로 두고 null로 지운다. 다른 필드를 고쳐도 유지된다
        fun overlap(node: JsonNode) = node["overlapCheck"].takeUnless { it.isNull }?.let { it["startTime"].asString() to it["endTime"].asString() }
        val withOverlap = read(mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"09:30","endTime":"18:00"}}""") }.andExpect { status { isOk() } })
        assertEquals(("09:30" to "18:00") to "1차 면접(온라인)", overlap(withOverlap) to withOverlap["title"].asString())
        assertEquals("09:30" to "18:00", overlap(read(mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"note":"공지 PDF 4쪽"}""") }.andExpect { status { isOk() } })))
        assertEquals("10:00" to "17:00", overlap(read(mvc.patch("/v1/admin/activities/$activity/schedules/$slotSchedule") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"10:00","endTime":"17:00"}}""") }.andExpect { status { isOk() } })))
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"18:00","endTime":"09:00"}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"9시","endTime":"18:00"}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity/schedules/$slotSchedule") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"10:00","endTime":"17:00"},"title":"x"}""") }.andExpect { status { isBadRequest() } }
        assertEquals(null, overlap(read(mvc.patch("/v1/admin/activities/$activity/schedules/$slotSchedule") { asAdmin(); patchBody("""{"overlapCheck":null}""") }.andExpect { status { isOk() } })))

        // 활동 PATCH·POST의 모집·활동 기간 객체 안 overlapCheck(어드민 웹이 쓰는 형태): 날짜와 함께 바꾸고, overlapCheck만 보내면 날짜는 그대로, null은 지운다
        val periods = read(mvc.patch("/v1/admin/activities/$activity") {
            asAdmin(); patchBody("""{"activitySchedule":{"startDate":"2026-12-01","endDate":"2026-12-03","overlapCheck":{"startTime":"13:00","endTime":"18:00"}},"application":{"startDate":"2026-11-01","endDate":"2026-11-10","overlapCheck":{"startTime":"09:00","endTime":"09:30"}}}""")
        }.andExpect { status { isOk() } })
        assertEquals(listOf("13:00" to "18:00", "09:00" to "09:30"), listOf(overlap(periods["activitySchedule"]), overlap(periods["application"])))
        val cleared = read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"application":{"overlapCheck":null}}""") }.andExpect { status { isOk() } })
        assertEquals(listOf(null, "2026-11-01"), listOf(overlap(cleared["application"]), cleared["application"]["startDate"].asString()))
        assertEquals("13:00" to "18:00", overlap(cleared["activitySchedule"]))
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"overlapCheck":{"startTime":"18:00","endTime":"13:00"}}}""") }.andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"overlapCheck":null}}""") }.andExpect { status { isOk() } }
        val posted = read(
            mvc.post("/v1/admin/activities") {
                asAdmin()
                body("""{"programId":$programId,"name":"겹침 확인 등록","description":"","officialUrl":"https://example.org/overlap","activitySchedule":{"startDate":"2027-01-10","endDate":"2027-01-11","overlapCheck":{"startTime":"10:00","endTime":"16:00"}},"application":{"endDate":"2027-01-05","overlapCheck":{"startTime":"20:00","endTime":"23:00"}}}""")
            }.andExpect { status { isCreated() } }
        )
        assertEquals(listOf("10:00" to "16:00", "20:00" to "23:00"), listOf(overlap(posted["activitySchedule"]), overlap(posted["application"])))

        // 반복 일정(admin-v1.md "반복 일정 — 2026-10-22"): recurrence만 보내면 그것만 바꾸고 정규화해 저장한다. 생략은 유지, null은 지운다
        fun rule(node: JsonNode) = node["recurrence"].takeUnless { it.isNull }?.asString()
        fun patchSchedule(target: Long, value: String) = mvc.patch("/v1/admin/activities/$activity/schedules/$target") { asAdmin(); patchBody(value) }
        val repeating = read(patchSchedule(scheduleId, """{"recurrence":"rrule:freq=weekly;byday=fr,mo,mo;interval=1;until=20270131"}""").andExpect { status { isOk() } })
        assertEquals(listOf("FREQ=WEEKLY;BYDAY=MO,FR;UNTIL=20270131", "1차 면접(온라인)"), listOf(rule(repeating), repeating["title"].asString()))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,FR;UNTIL=20270131", rule(read(patchSchedule(scheduleId, """{"note":"공지 PDF 5쪽"}""").andExpect { status { isOk() } })))
        assertEquals("FREQ=DAILY;INTERVAL=2;COUNT=10", rule(read(patchSchedule(scheduleId, """{"recurrence":"FREQ=DAILY;COUNT=10;INTERVAL=2"}""").andExpect { status { isOk() } })))
        // 허용 부분집합 밖, UNTIL·COUNT 둘 다·둘 다 없음, 범위 밖, 문자열 아님, UNTIL이 첫 날(2026-11-16) 앞이면 400
        listOf(
            "FREQ=MONTHLY;COUNT=3", "FREQ=WEEKLY;UNTIL=20270131;COUNT=3", "FREQ=WEEKLY;BYDAY=MO", "FREQ=DAILY;COUNT=401", "FREQ=WEEKLY;BYDAY=XX;COUNT=3",
            "FREQ=WEEKLY;INTERVAL=5;COUNT=3", "FREQ=WEEKLY;WKST=MO;COUNT=3", "FREQ=WEEKLY;COUNT=3;COUNT=4", "FREQ=WEEKLY;UNTIL=2027-01-31", "FREQ=WEEKLY;UNTIL=20261101",
        ).forEach { patchSchedule(scheduleId, """{"recurrence":"$it"}""").andExpect { status { isBadRequest() } } }
        patchSchedule(scheduleId, """{"recurrence":3}""").andExpect { status { isBadRequest() } }
        // 날짜만 있는 일정은 겹침 확인 시간이 회차 시간이라 반복이 남은 채로 지울 수 없다. 날짜 없는(문구만) 일정에는 둘 수 없다
        patchSchedule(scheduleId, """{"overlapCheck":null}""").andExpect { status { isBadRequest() } }
        patchSchedule(labelled["id"].asLong(), """{"recurrence":"FREQ=DAILY;COUNT=2"}""").andExpect { status { isBadRequest() } }
        assertEquals(null, rule(read(patchSchedule(scheduleId, """{"recurrence":null}""").andExpect { status { isOk() } })))
        // 시각 일정은 시작이 첫 회차다(UNTIL은 UTC 시각 형식도 받는다)
        val weekdays = read(mvc.post("/v1/admin/activities/$activity/schedules") {
            asAdmin(); body("""{"title":"정규 세션","kind":"EVENT","startAt":"2027-03-02T09:00:00+09:00","endAt":"2027-03-02T17:00:00+09:00","recurrence":"FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270831T145959Z"}""")
        }.andExpect { status { isCreated() } })
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270831T145959Z", rule(weekdays))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270831T145959Z", jdbc.sql("select recurrence from schedules where id = ?").param(weekdays["id"].asLong()).query(String::class.java).single())
        mvc.delete("/v1/admin/activities/$activity/schedules/${weekdays["id"].asLong()}") { asAdmin() }.andExpect { status { isNoContent() } }
        // 모집·활동 기간 객체 안 recurrence: 날짜만 있는 활동 기간은 겹침 확인 시간과 함께여야 하고, 둘만 보내면 날짜는 그대로다
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":{"recurrence":"FREQ=WEEKLY;BYDAY=TU;COUNT=3"}}""") }.andExpect { status { isBadRequest() } }
        val repeatingPeriod = read(mvc.patch("/v1/admin/activities/$activity") {
            asAdmin(); patchBody("""{"activitySchedule":{"overlapCheck":{"startTime":"09:00","endTime":"17:00"},"recurrence":"FREQ=WEEKLY;BYDAY=TU;COUNT=3"}}""")
        }.andExpect { status { isOk() } })
        assertEquals(listOf("FREQ=WEEKLY;BYDAY=TU;COUNT=3", "2026-12-01"), listOf(rule(repeatingPeriod["activitySchedule"]), repeatingPeriod["activitySchedule"]["startDate"].asString()))
        // 반복이 남은 채로 기간 날짜를 지우면 400이고 아무것도 바뀌지 않는다
        mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"activitySchedule":null}""") }.andExpect { status { isBadRequest() } }
        assertEquals("2026-12-01", jdbc.sql("select start_date::text from schedules where id = ?").param(slotSchedule).query(String::class.java).single())
        assertEquals(null, rule(read(patchSchedule(slotSchedule, """{"recurrence":null}""").andExpect { status { isOk() } })))

        // 일정 상태(native-v1.md "내 활동 서버 저장·일정 상태"): 지정 전에는 기본 세 개(id null), PUT은 순서대로 통째로 바꾼다
        fun statuses(node: JsonNode) = node["items"].toList().map { it["name"].asString() to it["kind"].asString() }
        fun putStatuses(target: Long, value: String) = mvc.put("/v1/admin/activities/$activity/schedules/$target/statuses") { asAdmin(); body(value) }
        val initial = read(mvc.get("/v1/admin/activities/$activity/schedules/$scheduleId/statuses") { asAdmin() }.andExpect { status { isOk() } })
        assertEquals(listOf("대기" to "WAITING", "통과" to "PASSED", "탈락" to "FAILED"), statuses(initial))
        assertEquals(true, initial["items"].toList().all { it["id"].isNull })
        val defined = read(putStatuses(scheduleId, """{"items":[{"name":"서류 대기","kind":"WAITING"},{"name":" 최종 합격 ","kind":"PASSED"},{"name":"보류","kind":"OTHER"}]}""").andExpect { status { isOk() } })
        assertEquals(listOf("서류 대기" to "WAITING", "최종 합격" to "PASSED", "보류" to "OTHER"), statuses(defined))
        val (waitingId, passedId) = defined["items"].toList().map { it["id"].asLong() }
        // 공개·어드민 일정 응답에도 같은 목록이 실린다
        assertEquals(listOf("서류 대기", "최종 합격", "보류"), read(mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("{}") })["statuses"].toList().map { it["name"].asString() })
        // 검증: 8개까지, 이름 1~20자·일정 안에서 겹치지 않음, 다른 일정의 id·모르는 종류는 400
        putStatuses(scheduleId, """{"items":[${(1..9).joinToString(",") { """{"name":"상태$it","kind":"OTHER"}""" }}]}""").andExpect { status { isBadRequest() } }
        putStatuses(scheduleId, """{"items":[{"name":"같음","kind":"WAITING"},{"name":"같음","kind":"PASSED"}]}""").andExpect { status { isBadRequest() } }
        putStatuses(scheduleId, """{"items":[{"name":"${"가".repeat(21)}","kind":"WAITING"}]}""").andExpect { status { isBadRequest() } }
        putStatuses(scheduleId, """{"items":[{"name":"x","kind":"MAYBE"}]}""").andExpect { status { isBadRequest() } }
        putStatuses(slotSchedule, """{"items":[{"id":$waitingId,"name":"x","kind":"WAITING"}]}""").andExpect { status { isBadRequest() } }
        // 지운 상태를 고른 사용자 선택은 그 상태의 기본 종류로 옮기고(OTHER는 지운다), 남긴 상태는 id를 지킨 채 이름을 바꾼다
        val user = java.util.UUID.randomUUID().also { jdbc.sql("insert into users (id) values (?)").param(it).update() }
        val userActivity = java.util.UUID.randomUUID()
        jdbc.sql("insert into user_activities (id, user_id, activity_id, created_at, updated_at) values (?, ?, ?, now(), now())").params(userActivity, user, activity).update()
        jdbc.sql("insert into user_activity_schedule_statuses (user_activity_id, schedule_id, schedule_status_id, updated_at) values (?, ?, ?, now())").params(userActivity, scheduleId, passedId).update()
        val labelledOther = read(putStatuses(labelled["id"].asLong(), """{"items":[{"name":"보류","kind":"OTHER"}]}""").andExpect { status { isOk() } })["items"][0]["id"].asLong()
        jdbc.sql("insert into user_activity_schedule_statuses (user_activity_id, schedule_id, schedule_status_id, updated_at) values (?, ?, ?, now())").params(userActivity, labelled["id"].asLong(), labelledOther).update()
        val renamedStatuses = read(putStatuses(scheduleId, """{"items":[{"id":$waitingId,"name":"서류 검토 중","kind":"WAITING"},{"name":"합격","kind":"PASSED"}]}""").andExpect { status { isOk() } })
        assertEquals(waitingId, renamedStatuses["items"][0]["id"].asLong())
        assertEquals(listOf("서류 검토 중" to "WAITING", "합격" to "PASSED"), statuses(renamedStatuses))
        assertEquals(listOf<Any?>(null, "PASSED"), jdbc.sql("select schedule_status_id, default_kind from user_activity_schedule_statuses where user_activity_id = ? and schedule_id = ?")
            .params(userActivity, scheduleId).query { rs, _ -> listOf(rs.getObject(1), rs.getString(2)) }.single())
        // 다른 일정의 OTHER 상태를 지우면 그 선택은 지워진다(기본 종류가 없다)
        putStatuses(labelled["id"].asLong(), """{"items":[{"name":"발표 대기","kind":"WAITING"}]}""").andExpect { status { isOk() } }
        assertEquals(0, jdbc.sql("select count(*) from user_activity_schedule_statuses where schedule_id = ?").param(labelled["id"].asLong()).query(Int::class.java).single())
        // 빈 목록은 기본 세 개로 되돌리고, 지운 상태를 고른 선택은 그 종류의 기본 상태로 옮긴다
        jdbc.sql("update user_activity_schedule_statuses set schedule_status_id = ?, default_kind = null where user_activity_id = ? and schedule_id = ?").params(waitingId, userActivity, scheduleId).update()
        val reset = read(putStatuses(scheduleId, """{"items":[]}""").andExpect { status { isOk() } })
        assertEquals(listOf("대기" to "WAITING", "통과" to "PASSED", "탈락" to "FAILED"), statuses(reset))
        assertEquals(true, reset["items"].toList().all { it["id"].isNull })
        assertEquals(0, jdbc.sql("select count(*) from schedule_statuses where schedule_id = ?").param(scheduleId).query(Int::class.java).single())
        assertEquals("WAITING", jdbc.sql("select default_kind from user_activity_schedule_statuses where user_activity_id = ? and schedule_id = ?").params(userActivity, scheduleId).query(String::class.java).single())
        // 만들 때도 겹침 확인 시간을 줄 수 있다(null은 없음)
        val createdWithOverlap = read(mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"OT","kind":"EVENT","dateLabel":"12월 1일","overlapCheck":{"startTime":"19:00","endTime":"21:00"}}""") }.andExpect { status { isCreated() } })
        assertEquals("19:00" to "21:00", overlap(createdWithOverlap))
        val createdWithoutOverlap = read(mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"OT2","kind":"EVENT","dateLabel":"12월 2일","overlapCheck":null}""") }.andExpect { status { isCreated() } })
        assertEquals(null, overlap(createdWithoutOverlap))
        listOf(createdWithOverlap, createdWithoutOverlap).forEach { mvc.delete("/v1/admin/activities/$activity/schedules/${it["id"].asLong()}") { asAdmin() }.andExpect { status { isNoContent() } } }
        mvc.post("/v1/admin/activities/$activity/schedules") { asAdmin(); body("""{"title":"OT3","kind":"EVENT","dateLabel":"12월 3일","overlapCheck":{"startTime":"21:00","endTime":"21:00"}}""") }.andExpect { status { isBadRequest() } }
        // 모집·활동 기간에도 상태를 둘 수 있다
        assertEquals(listOf("참가 확정" to "PASSED"), statuses(read(putStatuses(slotSchedule, """{"items":[{"name":"참가 확정","kind":"PASSED"}]}""").andExpect { status { isOk() } })))
        // 수집한(운영자 지정 아닌) 세부 일정에 상태를 두면 다시 수집해도 지워지지 않게 운영자 지정으로 남는다
        val collected = jdbc.sql("insert into schedules (activity_id, title, kind, status, date_label, sequence, created_at, updated_at) values (?, '수집 일정', 'EVENT', 'CONFIRMED', '1월', 0, localtimestamp, localtimestamp) returning id")
            .param(activity).query(Long::class.java).single()
        putStatuses(collected, """{"items":[{"name":"참석","kind":"PASSED"}]}""").andExpect { status { isOk() } }
        assertEquals(true, jdbc.sql("select curated from schedules where id = ?").param(collected).query(Boolean::class.java).single())
        // 겹침 확인 시간만 고쳐도 마찬가지
        val collectedOverlap = jdbc.sql("insert into schedules (activity_id, title, kind, status, date_label, sequence, created_at, updated_at) values (?, '수집 일정 2', 'EVENT', 'CONFIRMED', '2월', 0, localtimestamp, localtimestamp) returning id")
            .param(activity).query(Long::class.java).single()
        mvc.patch("/v1/admin/activities/$activity/schedules/$collectedOverlap") { asAdmin(); patchBody("""{"overlapCheck":{"startTime":"09:00","endTime":"10:00"}}""") }.andExpect { status { isOk() } }
        assertEquals(true, jdbc.sql("select curated from schedules where id = ?").param(collectedOverlap).query(Boolean::class.java).single())
        mvc.delete("/v1/admin/activities/$activity/schedules/$collectedOverlap") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/activities/$activity/schedules/$collected") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.get("/v1/admin/activities/$activity/schedules/999999/statuses") { asAdmin() }.andExpect { status { isNotFound() } }
        // 슬롯 일정(활동 기간)은 세부 일정 편집기로 고치거나 지울 수 없다. 없는 일정·다른 활동의 일정은 404
        mvc.patch("/v1/admin/activities/$activity/schedules/$slotSchedule") { asAdmin(); patchBody("""{"title":"x"}""") }.andExpect { status { isBadRequest() } }
        mvc.delete("/v1/admin/activities/$activity/schedules/$slotSchedule") { asAdmin() }.andExpect { status { isBadRequest() } }
        mvc.delete("/v1/admin/activities/$activity/schedules/999999") { asAdmin() }.andExpect { status { isNotFound() } }
        mvc.delete("/v1/admin/activities/999999/schedules/$scheduleId") { asAdmin() }.andExpect { status { isNotFound() } }
        // 지우면 응답에서 빠지고 행은 curated_deleted로 남는다. 지운 일정은 다시 고칠 수 없다(404)
        mvc.delete("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin() }.andExpect { status { isNoContent() } }
        assertEquals(listOf(labelled["id"].asLong()), listedSchedules())
        assertEquals(1, jdbc.sql("select count(*) from schedules where id = ? and curated_deleted").param(scheduleId).query(Int::class.java).single())
        mvc.patch("/v1/admin/activities/$activity/schedules/$scheduleId") { asAdmin(); patchBody("""{"title":"되살리기"}""") }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `초안도 검색되고, 게시하면 상태가 바뀐다`() {
        jdbc.sql("update activities set publication_status = 'DRAFT', official_source = false where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()

        val draft = read(mvc.get("/v1/admin/activities?publicationStatus=DRAFT&q=워크숍") { asAdmin() })["items"].single()
        assertEquals("DRAFT", draft["publicationStatus"].asString())
        assertEquals(false, draft["officialSource"].asBoolean())
        assertEquals("강남 코드스쿼드 오피스, 서울 강남구 강남대로62길 23 4층", draft["location"].asString())

        val published = read(
            mvc.patch("/v1/admin/activities/${draft["id"].asLong()}/publication-status") { asAdmin(); body("""{"publicationStatus":"PUBLISHED"}""") }
                .andExpect { status { isOk() } }
        )
        assertEquals("PUBLISHED", published["publicationStatus"].asString())
        mvc.patch("/v1/admin/activities/999999/publication-status") { asAdmin(); body("""{"publicationStatus":"PUBLISHED"}""") }
            .andExpect { status { isNotFound() } }
    }

    // 프로그램 로고(admin-v1.md "프로그램 로고"): PNG·JPEG·WebP 1MB 이하만, 있으면 바꾸고, 지우기는 없어도 204
    @Test
    fun `프로그램 로고는 올리면 logoUrl이 목록에 보이고, 종류·크기가 맞지 않으면 400, 지우면 없어진다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val url = "/v1/admin/programs/$programId/logo"
        fun upload(path: String, type: String, bytes: ByteArray) = mvc.multipart(HttpMethod.PUT, path) { file(MockMultipartFile("file", "logo", type, bytes)); asAdmin() }
        fun listed(): JsonNode = read(mvc.get("/v1/admin/programs?q=Let") { asAdmin() })["items"].single()["logoUrl"]
        try {
            mvc.get(url) { asAdmin() }.andExpect { status { isNotFound() } }
            assertEquals(true, listed().isNull)
            val bad = read(upload(url, "text/plain", byteArrayOf(1)).andExpect { status { isBadRequest() } })
            assertEquals("로고는 PNG·JPEG·WebP 1MB 이하여야 합니다", bad["detail"].asString())
            upload(url, "image/png", ByteArray(1024 * 1024 + 1)).andExpect { status { isBadRequest() } }
            upload("/v1/admin/programs/999999/logo", "image/png", byteArrayOf(1)).andExpect { status { isNotFound() } }
            mvc.multipart(HttpMethod.PUT, url) { file(MockMultipartFile("file", "logo", "image/png", byteArrayOf(1))) }.andExpect { status { isUnauthorized() } }
            val saved = read(upload(url, "image/png", byteArrayOf(1, 2, 3)).andExpect { status { isOk() } })
            val logoUrl = saved["logoUrl"].asString()
            assertEquals(true, Regex("/v1/programs/$programId/logo\\?v=\\d+").matches(logoUrl))
            assertEquals(logoUrl, listed().asString())
            val image = mvc.get(url) { asAdmin() }.andExpect { status { isOk() } }.andReturn().response
            assertEquals("image/png", image.contentType)
            assertEquals(listOf<Byte>(1, 2, 3), image.contentAsByteArray.toList())
            // 있으면 바꾼다(종류도 함께)
            upload(url, "image/webp", byteArrayOf(9)).andExpect { status { isOk() } }
            assertEquals("image/webp", mvc.get(url) { asAdmin() }.andReturn().response.contentType)
            mvc.delete(url) { asAdmin() }.andExpect { status { isNoContent() } }
            mvc.delete(url) { asAdmin() }.andExpect { status { isNoContent() } }
            mvc.get(url) { asAdmin() }.andExpect { status { isNotFound() } }
            assertEquals(true, listed().isNull)
        } finally {
            jdbc.sql("delete from program_logos where program_id = ?").param(programId).update()
        }
    }

    // 대기열 커버(admin-v1.md "프로그램 커버"): 로고와 같은 형식 검사(350KB), 출처 주소, 끄기·켜기, 프로그램 응답의 cover
    @Test
    fun `프로그램 커버는 출처와 함께 올리고 끄고 켤 수 있으며, 종류·크기·출처가 틀리면 400`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val url = "/v1/admin/programs/$programId/cover"
        fun upload(type: String, bytes: ByteArray, query: String = "") = mvc.multipart(HttpMethod.PUT, "$url$query") { file(MockMultipartFile("file", "cover", type, bytes)); asAdmin() }
        fun listed(): JsonNode = read(mvc.get("/v1/admin/programs?q=Let") { asAdmin() })["items"].single()["cover"]
        try {
            mvc.get(url) { asAdmin() }.andExpect { status { isNotFound() } }
            assertEquals(true, listed().isNull)
            assertEquals("커버는 PNG·JPEG·WebP 350KB 이하여야 합니다", read(upload("image/gif", byteArrayOf(1)).andExpect { status { isBadRequest() } })["detail"].asString())
            upload("image/webp", ByteArray(350 * 1024 + 1)).andExpect { status { isBadRequest() } }
            upload("image/webp", byteArrayOf(1), "?sourcePage=http://example.com").andExpect { status { isBadRequest() } }
            mvc.patch(url) { asAdmin(); body("""{"enabled":false}""") }.andExpect { status { isNotFound() } }
            val saved = read(upload("image/webp", byteArrayOf(4, 5), "?sourcePage=https://example.com/event&sourceImage=https://example.com/a.webp").andExpect { status { isOk() } })
            assertEquals(true, Regex("/v1/programs/$programId/cover\\?v=\\d+").matches(saved["coverUrl"].asString()))
            assertEquals(listOf("true", "https://example.com/event", "https://example.com/a.webp"), listOf(saved["enabled"].asString(), saved["sourcePage"].asString(), saved["sourceImage"].asString()))
            assertEquals(saved, listed())
            assertEquals(listOf<Byte>(4, 5), mvc.get(url) { asAdmin() }.andReturn().response.contentAsByteArray.toList())
            // 끄면 enabled=false(어드민 미리보기는 그대로), 다시 켤 수 있다
            assertEquals(false, read(mvc.patch(url) { asAdmin(); body("""{"enabled":false}""") }.andExpect { status { isOk() } })["enabled"].asBoolean())
            assertEquals(false, listed()["enabled"].asBoolean())
            mvc.get(url) { asAdmin() }.andExpect { status { isOk() } }
            assertEquals(true, read(mvc.patch(url) { asAdmin(); body("""{"enabled":true}""") }.andExpect { status { isOk() } })["enabled"].asBoolean())
            mvc.delete(url) { asAdmin() }.andExpect { status { isNoContent() } }
            assertEquals(true, listed().isNull)
        } finally {
            jdbc.sql("delete from program_covers where program_id = ?").param(programId).update()
        }
    }

    // 대기열 장면 글(native-v1.md "맞춤 대기열"): hook·highlights를 고치면 운영자 지정 필드로 남고, 빈 값은 지운다
    @Test
    fun `활동의 한 줄 소개와 핵심 줄을 고치면 운영자 지정으로 남고, 틀린 icon·길이는 400`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val id = read(
            mvc.post("/v1/admin/activities") {
                asAdmin(); body("""{"programId":$programId,"name":"대기열 글 활동","description":"d","officialUrl":"https://example.org/q","activitySchedule":{"dateLabel":"2027"}}""")
            }.andExpect { status { isCreated() } }
        )["id"].asLong()
        try {
            val patched = read(
                mvc.patch("/v1/admin/activities/$id") {
                    asAdmin(); patchBody("""{"hook":" 6주 만에 앱 하나를 끝까지 ","highlights":[{"icon":"team","text":"4명이 한 팀"},{"icon":"cost","text":"참가비 없음"}]}""")
                }.andExpect { status { isOk() } }
            )
            assertEquals("6주 만에 앱 하나를 끝까지", patched["hook"].asString())
            assertEquals("""[{"icon":"team","text":"4명이 한 팀"},{"icon":"cost","text":"참가비 없음"}]""", patched["highlights"].toString())
            assertEquals(true, patched["curatedFields"].toList().map { it.asString() }.containsAll(listOf("hook", "highlights")))
            // 모르는 icon, 4개, 61자 한 줄 소개는 400이고 아무것도 바꾸지 않는다
            mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"hook":"바뀌면 안 됨","highlights":[{"icon":"rocket","text":"x"}]}""") }.andExpect { status { isBadRequest() } }
            mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"highlights":[{"icon":"team","text":"a"},{"icon":"team","text":"b"},{"icon":"team","text":"c"},{"icon":"team","text":"d"}]}""") }.andExpect { status { isBadRequest() } }
            mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"hook":"${"가".repeat(61)}"}""") }.andExpect { status { isBadRequest() } }
            // 생략은 그대로, 빈 문자열·빈 배열은 지운다
            assertEquals("6주 만에 앱 하나를 끝까지", read(mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"nameEn":"Story"}""") })["hook"].asString())
            val cleared = read(mvc.patch("/v1/admin/activities/$id") { asAdmin(); patchBody("""{"hook":"","highlights":[]}""") }.andExpect { status { isOk() } })
            assertEquals(true, cleared["hook"].isNull)
            assertEquals(0, cleared["highlights"].size())
        } finally {
            jdbc.sql("delete from activities where id = ?").param(id).update()
        }
    }

    // 조직 로고(admin-v1.md "조직 로고"): 같은 파일 규칙, 조직 응답에 logoUrl, 프로그램 응답의 effectiveLogo는 자체 로고가 없으면 조직 로고를 계승한다
    @Test
    fun `조직 로고를 올리면 조직에 logoUrl이 보이고 자체 로고가 없는 프로그램은 그 로고를 계승한다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val organizationId = jdbc.sql("select organization_id from programs where id = ?").param(programId).query(Long::class.java).single()
        val url = "/v1/admin/organizations/$organizationId/logo"
        fun upload(path: String, type: String, bytes: ByteArray) = mvc.multipart(HttpMethod.PUT, path) { file(MockMultipartFile("file", "logo", type, bytes)); asAdmin() }
        fun program(): JsonNode = read(mvc.get("/v1/admin/programs?q=Let") { asAdmin() })["items"].single()
        val organizationName = jdbc.sql("select name from organizations where id = ?").param(organizationId).query(String::class.java).single()
        fun organization(): JsonNode = read(mvc.get("/v1/admin/organizations") { param("q", organizationName); asAdmin() })["items"].single { it["id"].asLong() == organizationId }
        try {
            mvc.get(url) { asAdmin() }.andExpect { status { isNotFound() } }
            assertEquals(true, organization()["logoUrl"].isNull)
            assertEquals(true, program()["effectiveLogo"].isNull)
            upload(url, "text/plain", byteArrayOf(1)).andExpect { status { isBadRequest() } }
            upload("/v1/admin/organizations/999999/logo", "image/png", byteArrayOf(1)).andExpect { status { isNotFound() } }
            val logoUrl = read(upload(url, "image/png", byteArrayOf(4, 5)).andExpect { status { isOk() } })["logoUrl"].asString()
            assertEquals(true, Regex("/v1/organizations/$organizationId/logo\\?v=\\d+").matches(logoUrl))
            assertEquals(logoUrl, organization()["logoUrl"].asString())
            assertEquals(listOf<Byte>(4, 5), mvc.get(url) { asAdmin() }.andExpect { status { isOk() } }.andReturn().response.contentAsByteArray.toList())
            program().let {
                assertEquals(true, it["logoUrl"].isNull)
                assertEquals(logoUrl, it["effectiveLogo"]["url"].asString())
                assertEquals("ORGANIZATION", it["effectiveLogo"]["source"].asString())
                assertEquals(organizationId, it["effectiveLogo"]["organizationId"].asLong())
            }
            // 자체 로고가 있으면 그것
            upload("/v1/admin/programs/$programId/logo", "image/png", byteArrayOf(1)).andExpect { status { isOk() } }
            assertEquals("PROGRAM", program()["effectiveLogo"]["source"].asString())
            mvc.delete(url) { asAdmin() }.andExpect { status { isNoContent() } }
            mvc.delete(url) { asAdmin() }.andExpect { status { isNoContent() } }
            assertEquals(true, organization()["logoUrl"].isNull)
        } finally {
            jdbc.sql("delete from program_logos where program_id = ?").param(programId).update()
            jdbc.sql("delete from organization_logos where organization_id = ?").param(organizationId).update()
        }
    }

    @Test
    fun `일정 블록에 트랙·세션 시간표를 통째로 붙이고 id로 고치며, 블록 밖·겹침·날짜 미정은 막는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val created = read(
            mvc.post("/v1/admin/activities") {
                asAdmin()
                body("""{"programId":$programId,"name":"시간표 편집 활동","description":"시간표 테스트","officialUrl":"https://example.org/timetable","activitySchedule":{"startDate":"2026-11-20","endDate":"2026-11-20"}}""")
            }.andExpect { status { isCreated() } }
        )
        val activity = created["id"].asLong()
        val slotSchedule = created["activitySchedule"]["id"].asLong()
        // 수집한(운영자 지정 아닌) 큰 블록: 오전 세션 10:50~12:30
        val block = jdbc.sql(
            """
            insert into schedules (activity_id, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, '오전 세션', 'EVENT', 'CONFIRMED', 'TIMED', '2026-11-20T10:50:00+09:00', '2026-11-20T12:30:00+09:00', 'Asia/Seoul', '', 0, localtimestamp, localtimestamp) returning id
            """
        ).param(activity).query(Long::class.java).single()
        val tagId = jdbc.sql("select id from tags order by id limit 1").query(Long::class.java).single()
        val path = "/v1/admin/activities/$activity/schedules/$block/timetable"
        fun put(target: String, value: String) = mvc.put(target) { asAdmin(); body(value) }
        fun session(track: String, title: String, start: String, end: String, kind: String = "TALK", id: Long? = null) =
            """{${id?.let { "\"id\":$it," } ?: ""}"track":$track,"kind":"$kind","title":"$title","startAt":"2026-11-20T$start:00+09:00","endAt":"2026-11-20T$end:00+09:00","speakers":[{"name":"김연사","affiliation":"우아한형제들"}],"tagIds":[$tagId]}"""

        assertEquals("""{"scheduleId":$block,"tracks":[],"sessions":[]}""", read(mvc.get(path) { asAdmin() }.andExpect { status { isOk() } }).toString())
        val first = read(
            put(path, """{"tracks":[{"name":"Track 1","room":"A홀"},{"name":"Track 2"}],"sessions":[${session("null", "키노트", "10:50", "11:30", "KEYNOTE")},${session("0", "트랙1 세션", "11:40", "12:20")},${session("1", "트랙2 세션", "11:40", "12:20")}]}""")
                .andExpect { status { isOk() } }
        )
        assertEquals(listOf("Track 1" to 0, "Track 2" to 1), first["tracks"].toList().map { it["name"].asString() to it["position"].asInt() })
        assertEquals(listOf(null, 0, 1), first["sessions"].toList().map { it["track"].takeUnless { n -> n.isNull }?.asInt() })
        assertEquals("김연사", first["sessions"][1]["speakers"][0]["name"].asString())
        assertEquals(tagId, first["sessions"][1]["tags"][0]["id"].asLong())
        // 시간표가 붙은 수집 블록은 운영자 지정으로 올라가(재수집이 지우지 않게) 응답에 hasTimetable
        assertEquals(true, jdbc.sql("select curated from schedules where id = ?").param(block).query(Boolean::class.java).single())
        val listed = read(mvc.get("/v1/admin/activities?q=시간표 편집 활동") { asAdmin() })["items"].toList().single { it["id"].asLong() == activity }["schedules"].toList().single()
        assertEquals(true, listed["hasTimetable"].asBoolean())
        assertEquals(true, read(mvc.patch("/v1/admin/activities/$activity/schedules/$block") { asAdmin(); body("""{"title":"오전 세션"}""") }.andExpect { status { isOk() } })["hasTimetable"].asBoolean())

        // id로 고치면 세션 찜이 남고, 빠진 세션·트랙은 지운다(그 세션 찜도). 트랙 순서를 바꿀 수 있다
        val (trackA, trackB) = first["tracks"].toList().map { it["id"].asLong() }
        val (keynote, talkA, talkB) = first["sessions"].toList().map { it["id"].asLong() }
        val user = java.util.UUID.randomUUID().also { jdbc.sql("insert into users (id) values (?)").param(it).update() }
        listOf(talkA, talkB).forEach { jdbc.sql("insert into user_session_favorites (user_id, session_id, created_at) values (?, ?, now())").params(user, it).update() }
        val second = read(
            put(path, """{"tracks":[{"id":$trackB,"name":"Track 2"},{"id":$trackA,"name":"Track 1 (본관)","room":"A홀"}],"sessions":[${session("null", "키노트", "10:50", "11:30", "KEYNOTE", keynote)},${session("1", "트랙1 세션(제목 수정)", "11:40", "12:20", id = talkA)}]}""")
                .andExpect { status { isOk() } }
        )
        assertEquals(listOf(trackB, trackA), second["tracks"].toList().map { it["id"].asLong() })
        assertEquals(listOf(keynote, talkA), second["sessions"].toList().map { it["id"].asLong() })
        assertEquals(trackA, second["sessions"][1]["trackId"].asLong())
        assertEquals(listOf(talkA), jdbc.sql("select session_id from user_session_favorites where user_id = ?").param(user).query(Long::class.java).list())

        // 막는 것: 블록 밖, 같은 트랙 겹침, 전폭과 겹침, 순번 밖 track, 없는 태그, 다른 블록의 id, 날짜 미정 블록, 없는 블록
        put(path, """{"tracks":[],"sessions":[${session("null", "늦은 세션", "12:20", "12:40")}]}""").andExpect { status { isBadRequest() } }
        put(path, """{"tracks":[{"name":"T"}],"sessions":[${session("0", "가", "11:00", "11:30")},${session("0", "나", "11:20", "11:50")}]}""").andExpect { status { isBadRequest() } }
        put(path, """{"tracks":[{"name":"T"}],"sessions":[${session("null", "가", "11:00", "11:30", "BREAK")},${session("0", "나", "11:20", "11:50")}]}""").andExpect { status { isBadRequest() } }
        put(path, """{"tracks":[],"sessions":[${session("3", "가", "11:00", "11:30")}]}""").andExpect { status { isBadRequest() } }
        put(path, """{"tracks":[],"sessions":[${session("null", "가", "11:00", "11:30").replace("[$tagId]", "[999999]")}]}""").andExpect { status { isBadRequest() } }
        put(path, """{"tracks":[],"sessions":[${session("null", "가", "11:00", "11:30", id = 999999)}]}""").andExpect { status { isBadRequest() } }
        val undated = jdbc.sql("insert into schedules (activity_id, title, kind, status, date_label, sequence, created_at, updated_at) values (?, '오후 세션', 'EVENT', 'CONFIRMED', '오후', 0, localtimestamp, localtimestamp) returning id")
            .param(activity).query(Long::class.java).single()
        put("/v1/admin/activities/$activity/schedules/$undated/timetable", """{"tracks":[],"sessions":[${session("null", "가", "11:00", "11:30")}]}""").andExpect { status { isBadRequest() } }
        put("/v1/admin/activities/$activity/schedules/999999/timetable", """{"tracks":[],"sessions":[]}""").andExpect { status { isNotFound() } }

        // 트랙이 없는 시간표(단일 진행)는 활동 기간(종일) 블록에도 붙는다. 빈 목록 둘이면 지운다
        val single = read(put("/v1/admin/activities/$activity/schedules/$slotSchedule/timetable", """{"tracks":[],"sessions":[${session("null", "단일 세션", "09:00", "10:00")}]}""").andExpect { status { isOk() } })
        assertEquals(0, single["tracks"].size())
        assertEquals(1, single["sessions"].size())
        assertEquals("""{"scheduleId":$block,"tracks":[],"sessions":[]}""", read(put(path, """{"tracks":[],"sessions":[]}""").andExpect { status { isOk() } }).toString())
        assertEquals(0, jdbc.sql("select count(*) from user_session_favorites where user_id = ?").param(user).query(Int::class.java).single())
        jdbc.sql("delete from users where id = ?").param(user).update()
    }
}
