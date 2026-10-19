package io.wid.dearby.adaptor.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// V5가 넣은 Let'Swift 활동과 테스트가 만든 활동으로 공개 조회·검색·페이지를 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class ActivityFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun search(query: String): JsonNode =
        json.readTree(mvc.get("/v1/activities?$query").andExpect { status { isOk() } }.andReturn().response.contentAsString)

    // Let'Swift 프로그램 아래에 이름이 다른 활동을 만든다. 모든 활동은 활동 기간 일정(slot ACTIVITY)을 하나 가진다
    private fun createActivity(name: String, status: String = "PUBLISHED"): Long {
        val id = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, publication_status, created_at, updated_at)
            select id, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', ?, localtimestamp, localtimestamp
            from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'
            """
        ).params(name, status).update().let {
            jdbc.sql("select id from activities where name = ?").param(name).query(Long::class.java).single()
        }
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at)
            values (?, 'ACTIVITY', ?, 'EVENT', 'CONFIRMED', '미정', 0, localtimestamp, localtimestamp)
            """
        ).params(id, name).update()
        return id
    }

    @Test
    fun `로그인 없이 활동을 소속 기관·프로그램과 일정까지 한 번에 받는다`() {
        val item = search("q=개발 환경의 변화")["items"].single()

        assertEquals("Let'Swift 2026 · 개발 환경의 변화 워크숍", item["name"].asString())
        assertEquals("Let’Swift 준비위원회", item["organization"]["name"].asString())
        assertEquals("Let’Swift", item["program"]["name"].asString())
        assertEquals("https://letswift.kr/2026/", item["officialUrl"].asString())
        // 관리자만 보는 정보는 내보내지 않는다
        assertEquals(setOf("id", "name", "description", "organization", "program", "officialUrl", "imageUrl", "application", "activitySchedule", "schedules", "eligibility", "category"), item.propertyNames().toSet())
        // 시각은 일정의 시간대로 표기한다(같은 순간)
        assertEquals("2026-10-08T00:00:00+09:00", item["application"]["endAt"].asString())
        assertEquals("2026-10-08", item["activitySchedule"]["dateLabel"].asString())
        assertEquals("Asia/Seoul", item["schedules"].single()["timeZone"].asString())
    }

    @Test
    fun `검색어는 대소문자를 가리지 않고 퍼센트 기호는 글자 그대로 찾는다`() {
        assertEquals(1, search("q=let'swift")["items"].size())
        assertEquals(0, search("q=%25")["items"].size())
    }

    @Test
    fun `오타나 띄어쓰기가 달라도 비슷한 활동을 찾고, 관계없는 말로는 찾지 않는다`() {
        assertEquals(1, search("q=워크샵")["items"].size())
        assertEquals(1, search("q=개발환경")["items"].size())
        assertEquals(0, search("q=디자인 컨퍼런스")["items"].size())
    }

    @Test
    fun `cursor로 다음 페이지를 이어 받는다`() {
        val first = createActivity("페이지 확인 A")
        val second = createActivity("페이지 확인 B")

        val page1 = search("q=페이지 확인&size=1")
        assertEquals(first, page1["items"].single()["id"].asLong())
        val page2 = search("q=페이지 확인&size=1&cursor=${page1["nextCursor"].asLong()}")
        assertEquals(second, page2["items"].single()["id"].asLong())
        assertNull(page2["nextCursor"].takeUnless { it.isNull })
    }

    @Test
    fun `기관으로 좁히고, 없는 기관이면 빈 목록이다`() {
        val organizationId = search("q=개발 환경의 변화")["items"].single()["organization"]["id"].asLong()
        assertEquals(1, search("q=개발 환경의 변화&organizationId=$organizationId")["items"].size())
        assertEquals(0, search("organizationId=-1")["items"].size())

        // 발견 프로그램 묶음(native-v1.md): V5의 Let'Swift 활동은 모집이 끝나 열린 활동이 없다. 지금 모집 중인 활동을 하나 만들면 그 프로그램만 묶음에 나온다
        fun programs(query: String): JsonNode =
            json.readTree(mvc.get("/v1/discovery/programs?$query").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals(0, programs("")["items"].size())
        val openId = createActivity("묶음 확인 모집 중")
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', now() - interval '1 day', now() + interval '7 days', 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
            """
        ).param(openId).update()
        val summary = programs("")["items"].single()
        assertEquals("Let’Swift", summary["name"].asString())
        assertEquals(organizationId, summary["organization"]["id"].asLong())
        assertEquals(1, summary["openActivityCount"].asInt())
        assertEquals(0, summary["matchedActivityIds"].size())
        // 활동 이름으로만 맞아도 프로그램이 나오고, 그 활동 id가 함께 온다. 프로그램 이름으로 맞으면 활동 id는 비어 있다
        val matched = programs("q=묶음 확인")["items"].single()["matchedActivityIds"]
        assertEquals(listOf(openId), (0 until matched.size()).map { matched[it].asLong() })
        assertEquals(0, programs("q=swift")["items"].single()["matchedActivityIds"].size())
        assertEquals(0, programs("q=디자인 컨퍼런스")["items"].size())
        assertEquals(0, programs("participationType=REGISTRATION")["items"].size())
        assertEquals(1, programs("participationType=selection")["items"].single()["openActivityCount"].asInt())
        mvc.get("/v1/discovery/programs?participationType=ANY").andExpect { status { isBadRequest() } }
        // 정렬 기준 보완(admin-v1.md): 모집 기간이 없는 활동은 활동 시작을 모집 마감으로 본다. 시작이 아직 오지 않았으면 열린 활동이고 가장 가까운 마감이 그 시작이 된다
        val upcomingId = createActivity("시작 예정 활동")
        jdbc.sql(
            """
            update schedules set time_type = 'ALL_DAY', start_date = (now() at time zone 'Asia/Seoul')::date + 3, end_date_exclusive = (now() at time zone 'Asia/Seoul')::date + 5
            where activity_id = ? and slot = 'ACTIVITY'
            """
        ).param(upcomingId).update()
        val withUpcoming = programs("")["items"].single()
        assertEquals(2, withUpcoming["openActivityCount"].asInt())
        val nearest = java.time.OffsetDateTime.parse(withUpcoming["nearestRecruitmentEndAt"].asString())
        val nearestSeoulDate = nearest.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
        assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(3), nearestSeoulDate)
        jdbc.sql("update activities set publication_status = 'DRAFT' where id = ?").param(upcomingId).update()
        // 활동 종류(native-v1.md "활동 종류"): 코드 하나 또는 쉼표 목록, 대소문자 무시. 열린 활동 수와 공개 응답의 category에 함께 적용된다
        jdbc.sql("update activities set category = 'BOOTCAMP' where id = ?").param(openId).update()
        assertEquals(1, programs("category=bootcamp,conference")["items"].single()["openActivityCount"].asInt())
        assertEquals(0, programs("category=CONFERENCE")["items"].size())
        assertEquals("BOOTCAMP", search("q=묶음 확인&category=BOOTCAMP")["items"].single()["category"].asString())
        assertEquals(0, search("q=묶음 확인&category=STUDY")["items"].size())
        mvc.get("/v1/activities?category=NOPE").andExpect { status { isBadRequest() } }
        mvc.get("/v1/discovery/programs?size=0").andExpect { status { isBadRequest() } }

        // 대상자 조건(native-v1.md "대상자 조건"): 나이 19~34 이고(재학 또는 미취업)인 활동. 조건이 없는 활동은 어떤 필터에도 통과한다
        jdbc.sql(
            """
            update activities set eligibility = '{"op":"all","items":[{"field":"나이","fieldEn":"age","from":19,"to":34},
              {"op":"any","items":[{"field":"재학상태","fieldEn":"educationStatus","values":["재학"],"valuesEn":["enrolled"]},
                                   {"field":"취업상태","fieldEn":"employment","values":["미취업"],"valuesEn":["unemployed"]}]}]}'::jsonb where id = ?
            """
        ).param(openId).update()
        jdbc.sql("insert into activity_eligibility_terms (activity_id, field_en, from_value, to_value, value_en) values (?, 'age', 19, 34, null), (?, 'educationStatus', null, null, 'enrolled'), (?, 'employment', null, null, 'unemployed')")
            .params(openId, openId, openId).update()
        val shown = json.readTree(mvc.get("/v1/activities/$openId").andExpect { status { isOk() } }.andReturn().response.contentAsString)["eligibility"]
        assertEquals("age", shown["items"][0]["fieldEn"].asString())
        // 검색어는 비슷한 이름의 다른 활동도 찾으므로 이 활동이 들어 있는지만 본다
        fun listed(query: String) = (0 until search(query)["items"].size()).any { search(query)["items"][it]["id"].asLong() == openId }
        assertEquals(true, listed("q=묶음 확인&age=24"))
        assertEquals(false, listed("q=묶음 확인&age=40"))
        // 요청한 필드마다 그 활동의 조건 중 하나는 맞아야 한다(1차). 맞는 조건이 있으면 op 평가(2차)도 지난다
        assertEquals(true, listed("q=묶음 확인&age=24&educationStatus=enrolled"))
        assertEquals(false, listed("q=묶음 확인&age=24&educationStatus=graduated"))
        assertEquals(false, listed("q=묶음 확인&employment=employed"))
        // 조건이 없는 Let'Swift 활동은 필터와 상관없이 나온다
        assertEquals(1, search("q=개발 환경의 변화&age=99")["items"].size())
        mvc.get("/v1/activities?age=abc").andExpect { status { isBadRequest() } }
        // 프로그램 묶음도 걸러진 활동으로 센다
        assertEquals(1, programs("age=24")["items"].single()["openActivityCount"].asInt())
        assertEquals(0, programs("age=40")["items"].size())
        mvc.get("/v1/discovery/programs?teamSize=x").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `id로 활동 하나를 목록 항목과 같은 형태로 받고, 없으면 404`() {
        val listed = search("q=개발 환경의 변화")["items"].single()
        val id = listed["id"].asLong()

        val item = json.readTree(mvc.get("/v1/activities/$id").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals(listed, item)
        mvc.get("/v1/activities/999999").andExpect { status { isNotFound() } }
        mvc.get("/v1/activities/abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `초안(DRAFT) 활동은 목록·검색·단건 어디에도 보이지 않는다`() {
        val id = createActivity("초안 활동 확인", "DRAFT")

        assertEquals(0, search("q=초안 활동 확인")["items"].size())
        mvc.get("/v1/activities/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `요청 언어가 영어면 영문 이름·설명을, 영문이 없거나 다른 언어면 한글을 준다`() {
        val id = createActivity("다국어 확인 활동")
        jdbc.sql("update activities set name_en = 'Language check', description_en = 'In English' where id = ?").param(id).update()
        fun get(language: String?) = mvc.get("/v1/activities/$id") { language?.let { header(HttpHeaders.ACCEPT_LANGUAGE, it) } }
            .andExpect { status { isOk() }; header { string(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE) } }.andReturn().response
        fun names(language: String?) = json.readTree(get(language).contentAsString).let {
            listOf(it["name"].asString(), it["description"].asString(), it["organization"]["name"].asString())
        }

        // 기관은 영문 이름이 없어 한글로 채운다
        assertEquals(listOf("Language check", "In English", "Let’Swift 준비위원회"), names("en-US,en;q=0.9"))
        assertEquals("en", get("en").getHeader(HttpHeaders.CONTENT_LANGUAGE))
        // 한국어가 더 선호되거나, 헤더가 없거나, 지원하지 않는 언어·읽을 수 없는 값이면 한국어
        listOf("ko-KR,en;q=0.5", null, "ja", "@@").forEach { assertEquals(listOf("다국어 확인 활동", "", "Let’Swift 준비위원회"), names(it), "$it") }
        assertEquals("ko", get(null).getHeader(HttpHeaders.CONTENT_LANGUAGE))
        // 검색은 영문 이름으로도 찾는다
        assertEquals(listOf(id), search("q=language check")["items"].values().map { it["id"].asLong() })
    }

    @Test
    fun `size가 범위를 벗어나면 400`() {
        mvc.get("/v1/activities?size=0").andExpect { status { isBadRequest() } }
        mvc.get("/v1/activities?size=101").andExpect { status { isBadRequest() } }
    }
}
