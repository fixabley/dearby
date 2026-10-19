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
import kotlin.test.assertTrue
import kotlin.test.assertNull

// V5가 넣은 Let'Swift 활동과 테스트가 만든 활동으로 공개 조회·검색·페이지를 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class ActivityFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    // JsonNode 배열을 Kotlin 목록으로(assertEquals의 타입 추론용)
    private fun tagNames(node: JsonNode): List<String> = node.toList().map { it["name"].asString() }
    private fun tagNamesEn(node: JsonNode): List<String?> = node.toList().map { it["nameEn"]?.takeUnless { n -> n.isNull }?.asString() }
    private fun idList(node: JsonNode): List<Long> = node.toList().map { it.asLong() }

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
        assertEquals("https://ticketa.co/e/vdb8aajs/l/website", item["applicationUrl"].asString()) // V5 application_url
        // 관리자만 보는 정보는 내보내지 않는다
        assertEquals(setOf("id", "name", "description", "organization", "program", "officialUrl", "applicationUrl", "imageUrl", "application", "activitySchedule", "schedules", "eligibility", "category", "tags", "location", "place"), item.propertyNames().toSet())
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
        // 열린 활동 판정 일원화(native-v1.md): 날짜 일정(ALL_DAY) 모집 기간도 서울 오늘을 포함하면 열린 활동이고, GET /v1/activities?open=true가 같은 규칙을 쓴다
        fun applicationAllDay(activityId: Long, startOffset: Int, endOffset: Int) = jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'ALL_DAY', (now() at time zone 'Asia/Seoul')::date + ?, (now() at time zone 'Asia/Seoul')::date + ?, '', 0, localtimestamp, localtimestamp)
            """
        ).params(activityId, startOffset, endOffset).update()
        val allDayOpenId = createActivity("종일 모집 중")
        applicationAllDay(allDayOpenId, -1, 2)
        val allDayEndedId = createActivity("종일 모집 끝")
        applicationAllDay(allDayEndedId, -10, 0)
        val timedClosedId = createActivity("시각 모집 끝")
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', now() - interval '10 days', now() - interval '1 hour', 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
            """
        ).param(timedClosedId).update()
        fun ids(page: JsonNode) = (0 until page["items"].size()).map { page["items"][it]["id"].asLong() }
        assertEquals(2, programs("")["items"].single()["openActivityCount"].asInt())
        // 검색은 단어 유사도까지 보므로 포함 여부로 확인한다
        val openSimilar = ids(search("open=true&q=종일 모집"))
        assertTrue(allDayOpenId in openSimilar && allDayEndedId !in openSimilar, openSimilar.toString())
        assertTrue(allDayEndedId in ids(search("q=종일 모집 끝")))
        assertTrue(timedClosedId in ids(search("q=시각 모집 끝")))
        assertTrue(timedClosedId !in ids(search("open=true&q=시각 모집 끝")))
        assertEquals(setOf(openId, allDayOpenId), ids(search("open=true")).toSet())
        jdbc.sql("update activities set publication_status = 'DRAFT' where id in (?, ?, ?)").params(allDayOpenId, allDayEndedId, timedClosedId).update()
        // 모집 중인 프로그램만 보기(native-v1.md): openOnly=false면 최근 3년 안에 활동이 있었던 프로그램까지, 열린 프로그램 먼저. 3년 넘게 지난 활동만 있는 프로그램은 빠진다
        fun pastProgram(name: String, daysAgo: Int): Long {
            jdbc.sql("insert into programs (organization_id, name, created_at, updated_at) values (?, ?, localtimestamp, localtimestamp)").params(organizationId, name).update()
            val programId = jdbc.sql("select id from programs where name = ?").param(name).query(Long::class.java).single()
            jdbc.sql(
                """
                insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, created_at, updated_at)
                values (?, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', 'PUBLISHED', localtimestamp - make_interval(days => ?), localtimestamp)
                """
            ).params(programId, "$name 활동", daysAgo).update()
            val activityId = jdbc.sql("select id from activities where name = ?").param("$name 활동").query(Long::class.java).single()
            jdbc.sql(
                """
                insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at)
                values (?, 'ACTIVITY', '활동 기간', 'EVENT', 'CONFIRMED', 'ALL_DAY', (now() at time zone 'Asia/Seoul')::date - ?, (now() at time zone 'Asia/Seoul')::date - ? + 3, '', 0, localtimestamp, localtimestamp)
                """
            ).params(activityId, daysAgo, daysAgo).update()
            return programId
        }
        val lastYear = pastProgram("지난해 프로그램", 400)
        val threeYearsAgo = pastProgram("삼년넘은 프로그램", 1200)
        val all = programs("openOnly=false")["items"]
        val names = (0 until all.size()).map { all[it]["name"].asString() }
        assertEquals("Let’Swift", names.first())
        assertTrue("지난해 프로그램" in names && "삼년넘은 프로그램" !in names, names.toString())
        val past = (0 until all.size()).map { all[it] }.single { it["id"].asLong() == lastYear }
        assertEquals(0, past["openActivityCount"].asInt())
        assertEquals(false, past["latestActivityAt"].isNull)
        assertEquals(1, programs("")["items"].size())
        jdbc.sql("delete from activities where program_id in (?, ?)").params(lastYear, threeYearsAgo).update()
        jdbc.sql("delete from programs where id in (?, ?)").params(lastYear, threeYearsAgo).update()
        // 마감 숨기기(native-v1.md, 2026-10-19 밤): 켜진 토글(openOnly=true)은 마감된 활동만 숨긴다. 모집이 내일 시작하는 프로그램은 모집 예정으로 나오고
        // (openActivityCount 0, upcomingActivityCount 1), 모집 중 프로그램 뒤에 선다. 어제 마감한 활동뿐인 프로그램은 빠진다
        fun programWithApplication(name: String, startInterval: String, endInterval: String): Long {
            jdbc.sql("insert into programs (organization_id, name, created_at, updated_at) values (?, ?, localtimestamp, localtimestamp)").params(organizationId, name).update()
            val programId = jdbc.sql("select id from programs where name = ?").param(name).query(Long::class.java).single()
            jdbc.sql(
                """
                insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, created_at, updated_at)
                values (?, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'VERIFIED', '', 'PUBLISHED', localtimestamp, localtimestamp)
                """
            ).params(programId, "$name 활동").update()
            val activityId = jdbc.sql("select id from activities where name = ?").param("$name 활동").query(Long::class.java).single()
            jdbc.sql(
                """
                insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
                values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', now() + ?::interval, now() + ?::interval, 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
                """
            ).params(activityId, startInterval, endInterval).update()
            return programId
        }
        val upcomingProgram = programWithApplication("모집 예정 프로그램", "1 day", "8 days")
        val closedProgram = programWithApplication("어제 마감 프로그램", "-10 days", "-1 day")
        val openOnlyItems = programs("")["items"]
        val openOnlyNames = (0 until openOnlyItems.size()).map { openOnlyItems[it]["name"].asString() }
        assertEquals(listOf("Let’Swift", "모집 예정 프로그램"), openOnlyNames)
        val upcomingSummary = openOnlyItems[1]
        assertEquals(0, upcomingSummary["openActivityCount"].asInt())
        assertEquals(1, upcomingSummary["upcomingActivityCount"].asInt())
        assertEquals(true, upcomingSummary["nearestRecruitmentEndAt"].isNull)
        assertTrue(java.time.OffsetDateTime.parse(upcomingSummary["nearestRecruitmentStartAt"].asString()).isAfter(java.time.OffsetDateTime.now()))
        assertEquals(0, openOnlyItems[0]["upcomingActivityCount"].asInt())
        // 검색어가 모집 예정 활동 이름에 맞아도 프로그램이 나오고 그 활동 id가 함께 온다
        assertEquals(1, programs("q=모집 예정 프로그램 활동")["items"].single()["matchedActivityIds"].size())
        assertTrue("어제 마감 프로그램" in (programs("openOnly=false")["items"].let { all2 -> (0 until all2.size()).map { all2[it]["name"].asString() } }))
        jdbc.sql("delete from activities where program_id in (?, ?)").params(upcomingProgram, closedProgram).update()
        jdbc.sql("delete from programs where id in (?, ?)").params(upcomingProgram, closedProgram).update()
        // 활동 종류(native-v1.md "활동 종류"): 코드 하나 또는 쉼표 목록, 대소문자 무시. 열린 활동 수와 공개 응답의 category에 함께 적용된다
        jdbc.sql("update activities set category = 'BOOTCAMP' where id = ?").param(openId).update()
        assertEquals(1, programs("category=bootcamp,conference")["items"].single()["openActivityCount"].asInt())
        assertEquals(0, programs("category=CONFERENCE")["items"].size())
        assertEquals("BOOTCAMP", search("q=묶음 확인&category=BOOTCAMP")["items"].single()["category"].asString())
        assertEquals(0, search("q=묶음 확인&category=STUDY")["items"].size())
        mvc.get("/v1/activities?category=NOPE").andExpect { status { isBadRequest() } }
        mvc.get("/v1/discovery/programs?size=0").andExpect { status { isBadRequest() } }

        // 태그(native-v1.md "태그"): 응답에 사전 순으로 실리고, 검색어는 태그 이름(한글·영문)에도 맞고(matchedActivityIds 포함), tagIds는 모두 가진 활동만(AND).
        // 묶음 머리의 tags는 프로그램 태그가 없으면 열린 활동 태그의 합집합
        val startup = jdbc.sql("select id from tags where name = '창업'").query(Long::class.java).single()
        val mentoring = jdbc.sql("select id from tags where name = '멘토링'").query(Long::class.java).single()
        val career = jdbc.sql("select id from tags where name = '취업'").query(Long::class.java).single()
        jdbc.sql("insert into activity_tags (activity_id, tag_id) values (?, ?), (?, ?)").params(openId, mentoring, openId, startup).update()
        // 검색어는 비슷한 이름의 다른 활동도 찾으므로 이 활동 항목만 본다
        fun openItem() = search("q=묶음 확인")["items"].toList().single { it["id"].asLong() == openId }
        assertEquals(listOf("창업", "멘토링"), openItem()["tags"].let(::tagNames))
        assertEquals(listOf<String?>("startup", "mentoring"), openItem()["tags"].let(::tagNamesEn))
        assertEquals(true, (0 until search("q=멘토링")["items"].size()).any { search("q=멘토링")["items"][it]["id"].asLong() == openId })
        assertEquals(listOf(openId), programs("q=mentoring")["items"].single()["matchedActivityIds"].let(::idList))
        assertEquals(listOf("창업", "멘토링"), programs("")["items"].single()["tags"].let(::tagNames))
        // 합집합은 마감된 게시 활동의 태그도 모은다(2026-10-20: 열린 활동만 모으면 마감된 프로그램 카드의 태그가 빈다). 최근 3년 창, 사전 순
        val creativity = jdbc.sql("select id from tags where name = '창의성'").query(Long::class.java).single()
        val closedTaggedId = createActivity("마감된 태그 활동")
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', now() - interval '10 days', now() - interval '1 hour', 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
            """
        ).param(closedTaggedId).update()
        jdbc.sql("insert into activity_tags (activity_id, tag_id) values (?, ?)").params(closedTaggedId, creativity).update()
        assertEquals(listOf("창업", "창의성", "멘토링"), programs("")["items"].single()["tags"].let(::tagNames))
        assertEquals(1, programs("")["items"].single()["openActivityCount"].asInt())
        jdbc.sql("update schedules set end_at = now() - interval '4 years', start_at = now() - interval '4 years 1 day' where activity_id = ? and slot = 'APPLICATION'").param(closedTaggedId).update()
        jdbc.sql("update activities set created_at = localtimestamp - interval '4 years' where id = ?").param(closedTaggedId).update()
        assertEquals(listOf("창업", "멘토링"), programs("")["items"].single()["tags"].let(::tagNames))
        jdbc.sql("update activities set publication_status = 'DRAFT' where id = ?").param(closedTaggedId).update()
        assertEquals(listOf(openId), search("tagIds=$startup,$mentoring")["items"].toList().map { it["id"].asLong() })
        assertEquals(0, search("tagIds=$startup,$career")["items"].size())
        assertEquals(1, programs("tagIds=$mentoring")["items"].single()["openActivityCount"].asInt())
        assertEquals(0, programs("tagIds=$career")["items"].size())
        mvc.get("/v1/activities?tagIds=abc").andExpect { status { isBadRequest() } }
        // 프로그램 태그가 있으면 묶음 머리는 그것을 쓴다. 공개 사전은 로그인 없이 sort_order 순
        val programId = programs("")["items"].single()["id"].asLong()
        jdbc.sql("insert into program_tags (program_id, tag_id) values (?, ?)").params(programId, career).update()
        assertEquals(listOf("취업"), programs("")["items"].single()["tags"].let(::tagNames))
        val dictionary = json.readTree(mvc.get("/v1/tags").andExpect { status { isOk() } }.andReturn().response.contentAsString)["items"]
        assertEquals(listOf("창업", "창의성"), listOf(dictionary[0]["name"].asString(), dictionary[1]["name"].asString()))
        jdbc.sql("delete from program_tags where program_id = ?").param(programId).update()
        jdbc.sql("delete from activity_tags where activity_id = ?").param(openId).update()

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
    fun `신청 링크는 activities의 application_url을 그대로 주고, 없으면 null`() {
        // native-v1.md "신청 확인 조건 완화·공개 applicationUrl": 어드민·수집기가 넣은 값을 목록·단건 모두 그대로 내보낸다
        val linked = createActivity("신청 링크 활동")
        jdbc.sql("update activities set application_url = 'https://example.org/apply' where id = ?").param(linked).update()
        val plain = createActivity("신청 링크 없는 활동")

        assertEquals("https://example.org/apply", search("q=신청 링크 활동")["items"].toList().single { it["id"].asLong() == linked }["applicationUrl"].asString())
        val detail = json.readTree(mvc.get("/v1/activities/$linked").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals("https://example.org/apply", detail["applicationUrl"].asString())
        assertTrue(json.readTree(mvc.get("/v1/activities/$plain").andExpect { status { isOk() } }.andReturn().response.contentAsString)["applicationUrl"].isNull)
    }

    @Test
    fun `활동 장소는 좌표가 있으면 place와 그 이름을, 없으면 장소 문구만 location으로 준다`() {
        // native-v1.md "활동 장소": 활동 기간(slot ACTIVITY) 행의 address_line1(이름)·address_line2(주소)·lat·lng. 문구만 있는 활동은 activities.location
        val placed = createActivity("장소 좌표 활동")
        jdbc.sql("update schedules set address_line1 = '코엑스 그랜드볼룸', address_line2 = '서울 강남구 영동대로 513', lat = 37.5116828, lng = 127.0594 where activity_id = ? and slot = 'ACTIVITY'")
            .param(placed).update()
        jdbc.sql("update activities set location = '온라인' where id = ?").param(placed).update()
        val textOnly = createActivity("장소 문구 활동")
        jdbc.sql("update activities set location = '서울 강남' where id = ?").param(textOnly).update()

        val listed = search("q=장소 좌표 활동")["items"].toList().single { it["id"].asLong() == placed }
        assertEquals("코엑스 그랜드볼룸", listed["location"].asString())
        assertEquals("""{"name":"코엑스 그랜드볼룸","address":"서울 강남구 영동대로 513","lat":37.5116828,"lng":127.0594}""", listed["place"].toString())
        val detail = json.readTree(mvc.get("/v1/activities/$placed").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals(listed["place"], detail["place"])
        assertEquals("코엑스 그랜드볼룸", detail["activitySchedule"]["address"]["addressLine1"].asString())

        val text = json.readTree(mvc.get("/v1/activities/$textOnly").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals("서울 강남", text["location"].asString())
        assertTrue(text["place"].isNull)
        val plain = json.readTree(mvc.get("/v1/activities/${createActivity("장소 없는 활동")}").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertTrue(plain["location"].isNull && plain["place"].isNull)
    }

    @Test
    fun `초안(DRAFT) 활동은 목록·검색·단건 어디에도 보이지 않는다`() {
        val id = createActivity("초안 활동 확인", "DRAFT")

        assertEquals(0, search("q=초안 활동 확인")["items"].size())
        mvc.get("/v1/activities/$id").andExpect { status { isNotFound() } }
    }

    // 활동 이력에서 단기 행사 제외(native-v1.md, 2026-10-20): forHistory=true는 활동 기간이 7일 미만인 행사와,
    // 기간을 모를 때 컨퍼런스·워크샵·밋업·강연을 뺀다. 기간은 지난 날짜로 두어 open=true 판정에 끼어들지 않게 한다
    @Test
    fun `forHistory=true는 7일 미만 행사와 기간 미상의 컨퍼런스·워크샵·밋업·강연을 뺀다`() {
        fun period(activityId: Long, days: Int) = jdbc.sql(
            "update schedules set time_type = 'ALL_DAY', start_date = date '2025-03-01', end_date_exclusive = date '2025-03-01' + ? where activity_id = ? and slot = 'ACTIVITY'"
        ).params(days, activityId).update()
        fun timedPeriod(activityId: Long, interval: String) = jdbc.sql(
            "update schedules set time_type = 'TIMED', time_zone = 'Asia/Seoul', start_at = timestamptz '2025-03-01 10:00+09', end_at = timestamptz '2025-03-01 10:00+09' + ?::interval where activity_id = ? and slot = 'ACTIVITY'"
        ).params(interval, activityId).update()
        fun category(activityId: Long, category: String) = jdbc.sql("update activities set category = ? where id = ?").params(category, activityId).update()
        val oneDayConference = createActivity("이력 후보 하루 컨퍼런스").also { period(it, 1); category(it, "CONFERENCE") }
        val sixDayBootcamp = createActivity("이력 후보 엿새 부트캠프").also { period(it, 6); category(it, "BOOTCAMP") }
        val sevenDayStudy = createActivity("이력 후보 이레 스터디").also { period(it, 7); category(it, "STUDY") }
        val tenDayBootcamp = createActivity("이력 후보 열흘 부트캠프").also { period(it, 10); category(it, "BOOTCAMP") }
        val timedShort = createActivity("이력 후보 시각 짧음").also { timedPeriod(it, "6 days 23 hours"); category(it, "BOOTCAMP") }
        val timedLong = createActivity("이력 후보 시각 긺").also { timedPeriod(it, "7 days"); category(it, "CONFERENCE") }
        val unknownStudy = createActivity("이력 후보 기간 미상 스터디").also { category(it, "STUDY") }
        val unknownConference = createActivity("이력 후보 기간 미상 컨퍼런스").also { category(it, "CONFERENCE") }
        val unknownNoCategory = createActivity("이력 후보 기간 미상 미분류")
        // 시각 일정에 시작이 없으면 기간 미상으로 보고 종류 규칙만 적용한다(2026-10-20 조율 결정)
        fun endOnly(activityId: Long) = jdbc.sql(
            "update schedules set time_type = 'TIMED', time_zone = 'Asia/Seoul', end_at = timestamptz '2025-03-01 10:00+09' where activity_id = ? and slot = 'ACTIVITY'"
        ).param(activityId).update()
        val endOnlyStudy = createActivity("이력 후보 끝만 스터디").also { endOnly(it); category(it, "STUDY") }
        val endOnlyConference = createActivity("이력 후보 끝만 컨퍼런스").also { endOnly(it); category(it, "CONFERENCE") }
        val draftLong = createActivity("이력 후보 초안", status = "DRAFT").also { period(it, 10) }
        fun ids(query: String): List<Long> = search(query)["items"].toList().map { it["id"].asLong() }
        val all = ids("q=이력 후보&size=50")
        assertTrue(listOf(oneDayConference, sixDayBootcamp, timedShort, unknownConference, endOnlyConference).all { it in all }, all.toString())
        assertTrue(draftLong !in all)
        assertEquals(setOf(sevenDayStudy, tenDayBootcamp, timedLong, unknownStudy, unknownNoCategory, endOnlyStudy), ids("q=이력 후보&forHistory=true&size=50").toSet())
        // cursor·size는 그대로 이어진다
        val firstPage = search("q=이력 후보&forHistory=true&size=2")
        assertEquals(2, firstPage["items"].size())
        assertEquals(4, ids("q=이력 후보&forHistory=true&size=50&cursor=${firstPage["nextCursor"].asLong()}").size)
        jdbc.sql("update activities set publication_status = 'DRAFT' where name like '이력 후보%'").update()
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

    // 프로그램 로고(native-v1.md "프로그램 로고"): 묶음의 logoUrl은 /v1/programs/{id}/logo?v={updated_at epoch 초}, 공개 GET은 인증 없이 영구 캐시 헤더로 낸다
    @Test
    fun `프로그램 로고가 있으면 묶음에 logoUrl이 오고, 공개 경로는 인증 없이 이미지를 준다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        fun summary(): JsonNode = json.readTree(mvc.get("/v1/discovery/programs?openOnly=false&q=swift").andExpect { status { isOk() } }.andReturn().response.contentAsString)["items"].single()
        assertEquals(true, summary()["logoUrl"].isNull)
        mvc.get("/v1/programs/$programId/logo").andExpect { status { isNotFound() } }
        jdbc.sql("insert into program_logos (program_id, content_type, data, updated_at) values (?, 'image/png', ?, timestamptz '2026-10-19T12:00:00Z')")
            .param(programId).param(byteArrayOf(1, 2, 3)).update()
        try {
            val v = java.time.Instant.parse("2026-10-19T12:00:00Z").epochSecond
            assertEquals("/v1/programs/$programId/logo?v=$v", summary()["logoUrl"].asString())
            val response = mvc.get("/v1/programs/$programId/logo?v=$v").andExpect { status { isOk() } }.andReturn().response
            assertEquals("image/png", response.contentType)
            assertEquals("public, max-age=31536000, immutable", response.getHeader(HttpHeaders.CACHE_CONTROL))
            assertEquals(listOf<Byte>(1, 2, 3), response.contentAsByteArray.toList())
        } finally {
            jdbc.sql("delete from program_logos where program_id = ?").param(programId).update()
        }
    }
}
