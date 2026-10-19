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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
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

    private val json = JsonMapper.builder().build()

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
        assertEquals(true, read(mvc.patch("/v1/admin/activities/$activity") { asAdmin(); patchBody("""{"eligibility":null}""") }.andExpect { status { isOk() } })["eligibility"].isNull)
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
        mvc.delete("/v1/admin/watchlist/${alias["id"].asLong()}") { asAdmin() }.andExpect { status { isNoContent() } }
        mvc.delete("/v1/admin/watchlist/${alias["id"].asLong()}") { asAdmin() }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `수집 설정은 https 주소와 호스트 이름만 받는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val url = "/v1/admin/programs/$programId/collection-settings"
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"http://letswift.kr","allowedHosts":["letswift.kr"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["https://letswift.kr/2026"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["letswift.kr"],"intervalDays":0,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        val saved = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://letswift.kr/2026/","allowedHosts":["letswift.kr"],"intervalDays":3,"enabled":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(3, saved["intervalDays"].asInt())
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
}
