package io.wid.dearby.batch

import io.wid.dearby.batch.application.ActivityDiscoverer
import io.wid.dearby.batch.application.ActivityExtractor
import io.wid.dearby.batch.adaptor.claude.CollectionPrompts
import io.wid.dearby.domain.collection.DateLabelExample
import io.wid.dearby.batch.application.Discovery
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.Extraction
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.ModelUsage
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.batch.application.PageFetcher
import io.wid.dearby.batch.application.RenderingPageFetcher
import io.wid.dearby.batch.job.CollectionJobConfig
import io.wid.dearby.batch.recall.RecallService
import io.wid.dearby.domain.collection.CollectionUrlTemplate
import java.time.LocalDate
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import io.wid.dearby.TestDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 모델·네트워크 대신 가짜 구현으로 잡 전체(설정 읽기 → 대조 → 초안 저장 → 기록)를 확인한다. API 비용이 들지 않는다
// batch 앱은 io.wid.dearby.batch만 스캔하므로 공유 테스트 DB 설정(스키마 비우고 다시 마이그레이션)을 직접 가져온다
@SpringBootTest(properties = ["spring.main.keep-alive=false"])
@Import(TestDatabaseConfig::class)
class CollectionJobTest(
    @Autowired val jobOperator: JobOperator,
    @Autowired val collectActivitiesJob: Job,
    @Autowired val jdbc: JdbcClient,
    @Autowired val recall: RecallService,
) {

    @TestConfiguration
    class Fakes {
        companion object {
            // 마지막 추출 요청이 받은 날짜 문구 해석 예시(admin-v1.md "날짜 문구 피드백")
            var receivedExamples: List<DateLabelExample> = emptyList()
            var extractedCategory: String? = "CONFERENCE" // 프로그램 기본 종류 테스트에서 null로 바꾼다
            // 브라우저 렌더링(admin-v1.md): 렌더링 경로로 받은 주소와 HTML 경로로 받은 주소, 브라우저를 내린 횟수
            val renderedUrls = mutableListOf<String>()
            val httpUrls = mutableListOf<String>()
            var rendererClosed = 0
            var extractedTags: List<String> = listOf("창업", "없는 태그") // 태그 테스트: 사전 밖 이름은 버려진다
            var receivedTagNames: List<String> = emptyList() // 마지막 추출 요청이 받은 태그 사전
        }

        @Bean
        @Primary
        fun fakeDiscoverer() = ActivityDiscoverer { target ->
            // 공식 도메인이 있으면 그 안의 없는 페이지, 없으면 웹 전체에서 찾은 블로그 글
            val url = if (target.officialOnly) "https://letswift.kr/2026/missing" else "https://blog.example/letswift-2026"
            Discovery(listOf(url), ModelUsage(100, 10, 1))
        }

        @Bean
        @Primary
        fun fakeFetcher() = PageFetcher { url, _ ->
            httpUrls += url
            if (url.endsWith("missing")) throw PageFetchException("HTTP 404")
            FetchedPage(url, "Let'Swift 2026 컨퍼런스 참가 신청은 11월 1일부터 11월 10일까지입니다.", setOf("https://letswift.kr/2026/apply"))
        }

        @Bean
        @Primary
        fun fakeRenderer() = object : RenderingPageFetcher {
            override fun fetch(url: String, allowedHosts: List<String>): FetchedPage {
                renderedUrls += url
                if (url.endsWith("missing")) throw PageFetchException("렌더링 실패: HTTP 404")
                return FetchedPage(url, "(렌더링됨) Let'Swift 2026 컨퍼런스 참가 신청은 11월 1일부터 11월 10일까지입니다.", emptySet())
            }
            override fun close() { rendererClosed++ }
        }

        @Bean
        @Primary
        fun fakeExtractor() = ActivityExtractor { _, _, _, _, examples, tagNames ->
            receivedExamples = examples
            receivedTagNames = tagNames
            Extraction(
                listOf(
                    ExtractedActivity(
                        roundKey = "2026-컨퍼런스", name = "Let'Swift 2026 컨퍼런스", nameEn = "Let'Swift 2026 Conference", description = "본행사", descriptionEn = "Main event", participationType = "REGISTRATION",
                        applicationUrl = "https://letswift.kr/2026/apply", recruitmentStartAt = "2026-11-01T10:00:00+09:00",
                        recruitmentEndAt = "2026-11-10T23:59:00+09:00", dateLabel = "2026-11-29", startDate = "2026-11-29", endDate = "2026-11-29", schedules = emptyList(),
                        location = null, cost = null, audience = null, evidenceQuote = "참가 신청은 11월 1일부터 11월 10일까지입니다", category = extractedCategory, tags = extractedTags,
                    ),
                    ExtractedActivity(
                        roundKey = "2026-가짜", name = "원문에 없는 활동", nameEn = null, description = "", descriptionEn = null, participationType = null,
                        applicationUrl = null, recruitmentStartAt = null, recruitmentEndAt = null, dateLabel = "",
                        schedules = emptyList(), location = null, cost = null, audience = null, evidenceQuote = "원문 어디에도 없는 문장입니다",
                    ),
                ),
                ModelUsage(1000, 200),
            )
        }
    }

    @BeforeTest
    fun clean() {
        jdbc.sql("delete from program_collection_settings").update()
        jdbc.sql("delete from collection_runs").update()
        jdbc.sql("delete from dismissed_activity_sources").update()
        jdbc.sql("delete from collection_watchlist").update()
        jdbc.sql("delete from schedules where curated and slot is null").update()
        // V5로 넣은 Let'Swift 활동만 남기고 수집으로 생긴 활동을 지운다(일정은 함께 지워진다)
        jdbc.sql("delete from activities where source_id is distinct from '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
    }

    // Let'Swift 프로그램의 수집 설정: 공식 도메인 letswift.kr 안에서만, intervalDays마다
    private fun officialSetting(intervalDays: Int = 1, url: String = "https://letswift.kr/2026/", renderPages: Boolean = false) = jdbc.sql(
        """
        insert into program_collection_settings (program_id, official_url, allowed_hosts, interval_days, render_pages, created_at, updated_at)
        select id, ?, array['letswift.kr'], ?, ?, localtimestamp, localtimestamp
        from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'
        """
    ).param(url).param(intervalDays).param(renderPages).update()

    private fun runCount() = jdbc.sql("select count(*) from collection_runs").query(Int::class.java).single()

    private fun run(date: String) = jobOperator.start(
        collectActivitiesJob,
        JobParametersBuilder().addString(CollectionJobConfig.RUN_DATE, date).addString("trigger", "test-${System.nanoTime()}").toJobParameters(),
    )

    private fun status(name: String) =
        jdbc.sql("select publication_status from activities where name = ?").param(name).query(String::class.java).list()

    @Test
    fun `렌더링 설정을 켠 프로그램은 브라우저 렌더링으로 받고 그 본문이 추출·대조에 들어간다`() {
        Fakes.renderedUrls.clear(); Fakes.httpUrls.clear(); Fakes.rendererClosed = 0
        officialSetting(renderPages = true)
        assertEquals(BatchStatus.COMPLETED, run("2026-10-22").status)
        // 시작 주소와 탐색 주소 모두 렌더링 경로로 받고, HTML 받기는 쓰지 않는다. 404였던 탐색 주소는 거절 이유로 남는다
        assertEquals(listOf("https://letswift.kr/2026/", "https://letswift.kr/2026/missing"), Fakes.renderedUrls)
        assertEquals(emptyList(), Fakes.httpUrls)
        assertEquals("https://letswift.kr/2026/", jdbc.sql("select array_to_string(fetched_urls, ',') from collection_runs").query(String::class.java).single())
        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(1, jdbc.sql("select cardinality(array_positions(rejected_reasons, 'https://letswift.kr/2026/missing: 렌더링 실패: HTTP 404')) from collection_runs").query(Int::class.java).single())
        // 프로그램 수집이 끝나면 브라우저를 내린다
        assertEquals(1, Fakes.rendererClosed)
        // 렌더링을 끈 프로그램은 HTML 경로다
        jdbc.sql("update program_collection_settings set render_pages = false").update()
        jdbc.sql("delete from collection_runs").update()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-23").status)
        assertEquals(2, Fakes.renderedUrls.size)
        assertEquals(2, Fakes.httpUrls.size)
        assertEquals(1, Fakes.rendererClosed)
    }

    @Test
    fun `원문으로 확인된 활동만 초안으로 저장하고, 같은 날은 다시 수집하지 않는다`() {
        officialSetting()
        // 이전 기수(V5의 Let'Swift 활동)에 대상 원문·대상자 조건을 두면, 본문에 없던 새 초안이 그 값을 가져오고 inherited_fields에 남긴다
        jdbc.sql("update activities set audience = 'Swift 개발자', eligibility = '{\"op\":\"all\",\"items\":[{\"field\":\"나이\",\"fieldEn\":\"age\",\"from\":19}]}'::jsonb where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-20").status)
        assertEquals(
            listOf("Swift 개발자|19|[\"eligibility\", \"audience\"]"),
            jdbc.sql("select audience || '|' || (eligibility->'items'->0->>'from') || '|' || inherited_fields::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
        assertEquals(1, jdbc.sql("select count(*) from activity_eligibility_terms t join activities a on a.id = t.activity_id where a.name = ?").param("Let'Swift 2026 컨퍼런스").query(Int::class.java).single())

        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(emptyList(), status("원문에 없는 활동"))
        val record = jdbc.sql("select outcome, found_count, saved_count, cardinality(rejected_reasons), input_tokens, web_search_requests, official_only from collection_runs")
            .query { rs, _ -> listOf(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7)) }.single()
        // 페이지 2개 중 1개는 404, 받은 페이지에서 2건을 찾아 1건 저장·1건 제외
        assertEquals(listOf<Any>("SUCCEEDED", 2, 1, 2, 1100L, 1, true), record)
        // 실제로 받은 페이지만 fetched_urls에 남는다(404였던 탐색 주소는 빠진다). 활동 종류는 모델이 뽑은 값 그대로 초안에
        assertEquals("https://letswift.kr/2026/", jdbc.sql("select array_to_string(fetched_urls, ',') from collection_runs").query(String::class.java).single())
        assertEquals(listOf("CONFERENCE"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        // 태그(native-v1.md "태그"): 모델이 사전 이름으로 고른 것만 남고(없는 태그는 버림), 추출 요청에는 사전 전체가 넘어간다
        assertEquals(listOf("창업"), jdbc.sql("select t.name from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        assertTrue(Fakes.receivedTagNames.containsAll(listOf("창업", "멘토링")))
        assertEquals(listOf(true), jdbc.sql("select official_source from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(Boolean::class.java).list())
        assertEquals(listOf("Let'Swift 2026 Conference|Main event"), jdbc.sql("select name_en || '|' || description_en from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        val schedules = jdbc.sql("select slot from schedules s join activities a on a.id = s.activity_id where a.name = ? order by slot")
            .param("Let'Swift 2026 컨퍼런스").query(String::class.java).list()
        assertEquals(listOf("ACTIVITY", "APPLICATION"), schedules)
        // 날짜만 있는 활동 기간은 종일 일정(끝 날짜 포함 → exclusive는 하루 뒤)
        assertEquals(listOf("ALL_DAY|2026-11-29|2026-11-30"), jdbc.sql("select time_type || '|' || start_date || '|' || end_date_exclusive from schedules s join activities a on a.id = s.activity_id where a.name = ? and slot = 'ACTIVITY'")
            .param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())

        run("2026-10-20") // 같은 날짜를 다시 실행해도 수집하지 않는다
        assertEquals(1, runCount())

        // 운영자 지정 필드(admin-v1.md): curated_fields에 있는 필드만 지키고, 전에 수집·가져온 값은 다시 수집하면 새 값으로 갱신한다
        jdbc.sql("insert into activity_tags (activity_id, tag_id) select a.id, t.id from activities a, tags t where a.name = ? and t.name = '멘토링' on conflict do nothing").param("Let'Swift 2026 컨퍼런스").update()
        jdbc.sql("update activities set category = 'STUDY', curated_fields = '[\"eligibility\",\"nameEn\",\"category\",\"tags\"]'::jsonb, name_en = 'Curated EN', eligibility = '{\"op\":\"all\",\"items\":[{\"field\":\"나이\",\"fieldEn\":\"age\",\"from\":30}]}'::jsonb, description = '옛 설명', audience = '옛 대상', inherited_fields = '[]'::jsonb where name = ?")
            .param("Let'Swift 2026 컨퍼런스").update()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-21").status)
        assertEquals(2, runCount())
        assertEquals(listOf("STUDY"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        // 운영자가 지정한 태그(curated tags)는 다시 수집해도 그대로
        assertEquals(listOf("창업", "멘토링"), jdbc.sql("select t.name from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ? order by t.sort_order").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        assertEquals(
            listOf("Curated EN|본행사|Swift 개발자|30|[\"audience\"]"),
            jdbc.sql("select name_en || '|' || description || '|' || audience || '|' || (eligibility->'items'->0->>'from') || '|' || inherited_fields::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
    }

    @Test
    fun `시작 주소 토큰은 올해·내년, 다음·현재 기수로 펼치고 기수를 모르면 건너뛴다`() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(listOf("https://toss.im/slash-27", "https://toss.im/slash-26"), CollectionUrlTemplate.expand("https://toss.im/slash-{yy}", today, null))
        assertEquals(listOf("https://x.kr/2027", "https://x.kr/2026"), CollectionUrlTemplate.expand("https://x.kr/{year}", today, 10))
        assertEquals(listOf("https://x.kr/11", "https://x.kr/10"), CollectionUrlTemplate.expand("https://x.kr/{round}", today, 10))
        assertEquals(
            listOf("https://x.kr/2027/11", "https://x.kr/2027/10", "https://x.kr/2026/11", "https://x.kr/2026/10"),
            CollectionUrlTemplate.expand("https://x.kr/{year}/{round}", today, 10),
        )
        assertEquals(emptyList(), CollectionUrlTemplate.expand("https://x.kr/{round}", today, null))
        assertEquals(listOf("https://x.kr/fixed"), CollectionUrlTemplate.expand("https://x.kr/fixed", today, null))
        assertEquals(listOf("season"), CollectionUrlTemplate.unknownTokens("https://x.kr/{season}/{yy}"))
        assertEquals(10, CollectionUrlTemplate.previousRound("부스트캠프 Web·Mobile 10기"))
        assertEquals(24, CollectionUrlTemplate.previousRound("SLASH 24"))
        assertEquals(null, CollectionUrlTemplate.previousRound("Let'Swift 2026 컨퍼런스"))
        assertEquals(null, CollectionUrlTemplate.previousRound(null))

        // 수집: {round}가 있는데 이전 활동 이름에 기수가 없으면 시작 주소를 건너뛰고 거절 이유에 남긴다(탐색 주소는 404라 받은 페이지가 없어 FAILED)
        officialSetting(url = "https://letswift.kr/{round}/")
        assertEquals(BatchStatus.COMPLETED, run("2026-10-20").status)
        val reasons = jdbc.sql("select outcome || '|' || array_to_string(rejected_reasons, ';') from collection_runs").query(String::class.java).single()
        assertEquals(true, reasons.startsWith("FAILED|") && reasons.contains("https://letswift.kr/{round}/: 기수 번호를 알 수 없음"), reasons)
        assertEquals("", jdbc.sql("select array_to_string(fetched_urls, ',') from collection_runs").query(String::class.java).single())
    }

    @Test
    fun `운영자가 지정한 기간의 원문 문구는 다음 수집의 해석 예시로 지시문에 들어간다`() {
        officialSetting()
        // 어드민이 날짜를 직접 지정한 일정(curated, 원문 문구 있음)을 V5 Let'Swift 활동에 하나 둔다
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at, curated)
            select id, null, '해커톤', 'EVENT', 'CONFIRMED', 'ALL_DAY', date '2026-11-01', date '2026-11-16', '2026년 11월 1일~15일', 99, localtimestamp, localtimestamp, true
            from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'
            """
        ).update()
        run("2026-10-19")
        val example = Fakes.receivedExamples.single { it.dateLabel == "2026년 11월 1일~15일" }
        assertEquals("[일정] \"2026년 11월 1일~15일\" → startDate 2026-11-01, endDate 2026-11-15", CollectionPrompts.dateExampleLine(example))
        val prompt = CollectionPrompts.extractRequest("Let'Swift", "https://letswift.kr/2026/", "본문", examples = Fakes.receivedExamples)
        assertTrue(prompt.contains("날짜 문구 해석 예시(운영자가 바로잡은 것):\n- [일정] \"2026년 11월 1일~15일\" → startDate 2026-11-01, endDate 2026-11-15"))
        assertTrue(prompt.contains("비슷한 문구는 같은 방식으로 날짜 필드를 채운다"))
        // 예시가 없으면 절 자체가 없다
        assertFalse(CollectionPrompts.extractRequest("Let'Swift", "https://letswift.kr/2026/", "본문").contains("날짜 문구 해석 예시"))
    }

    @Test
    fun `모델이 종류를 못 뽑으면 프로그램 기본 종류를 초안에 쓴다`() {
        officialSetting()
        jdbc.sql("update programs set category = 'MEETUP' where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").update()
        // 태그도 같다: 모델이 하나도 못 고르면 프로그램 태그를 기본으로
        jdbc.sql("insert into program_tags (program_id, tag_id) select p.id, t.id from programs p, tags t where p.source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db' and t.name = '협업'").update()
        Fakes.extractedCategory = null
        Fakes.extractedTags = emptyList()
        try {
            assertEquals(BatchStatus.COMPLETED, run("2026-10-22").status)
            assertEquals(listOf("MEETUP"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
            assertEquals(listOf("협업"), jdbc.sql("select t.name from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        } finally {
            Fakes.extractedCategory = "CONFERENCE"
            Fakes.extractedTags = listOf("창업", "없는 태그")
            jdbc.sql("delete from program_tags").update()
            jdbc.sql("update programs set category = null where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").update()
        }
    }

    @Test
    fun `주기를 정하면 마지막 성공 수집부터 그 일수 동안은 수집하지 않는다`() {
        officialSetting(intervalDays = 7)

        run("2026-10-20")
        run("2026-10-26") // 6일 뒤: 주기 안
        assertEquals(1, runCount())
        run("2026-10-27") // 7일 뒤: 다시 수집
        assertEquals(2, runCount())
    }

    @Test
    fun `설정이 없는 프로그램은 웹 전체에서 찾고, 그 초안은 공식 출처가 아님으로 남긴다`() {
        run("2026-10-20")

        assertEquals(listOf(false), jdbc.sql("select official_source from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(Boolean::class.java).list())
        assertEquals(listOf("https://blog.example/letswift-2026"), jdbc.sql("select official_url from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        assertEquals(false, jdbc.sql("select official_only from collection_runs").query(Boolean::class.java).single())
    }

    @Test
    fun `관리자가 삭제한 수집 활동은 다음 수집에서 다시 만들지 않는다`() {
        officialSetting()
        run("2026-10-21")
        val sourceId = jdbc.sql("select source_id::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).single()
        // 어드민 삭제와 같은 결과: 활동을 지우고 출처를 남긴다
        jdbc.sql("insert into dismissed_activity_sources (source_id, dismissed_at) values (?::uuid, now())").param(sourceId).update()
        jdbc.sql("delete from activities where source_id = ?::uuid").param(sourceId).update()

        run("2026-10-22")

        assertEquals(emptyList(), status("Let'Swift 2026 컨퍼런스"))
        val reasons = jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs where run_date = '2026-10-22'").query(String::class.java).single()
        assertEquals(true, reasons.contains("관리자가 삭제한 활동"))

        // 조심 목록(admin-v1.md)의 제외 이름은 저장 전에 걸러지고 사유에 남는다(dismissed보다 먼저 판정)
        jdbc.sql("insert into collection_watchlist (program_id, name, normalized_name, kind, created_at) select id, 'Let''Swift 2026  컨퍼런스', 'let''swift2026컨퍼런스', 'EXCLUDE', now() from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").update()
        run("2026-10-23")
        val excluded = jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs where run_date = '2026-10-23'").query(String::class.java).single()
        assertEquals(true, excluded.contains("조심 목록: 제외"))
    }

    @Test
    fun `게시된 활동은 다시 수집해도 덮어쓰지 않는다`() {
        officialSetting()
        run("2026-10-21")
        jdbc.sql("update activities set publication_status = 'PUBLISHED', name = '관리자가 고친 이름' where name = ?").param("Let'Swift 2026 컨퍼런스").update()

        run("2026-10-22")

        assertEquals(listOf("PUBLISHED"), status("관리자가 고친 이름"))
        val reasons = jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs where run_date = '2026-10-22'").query(String::class.java).single()
        assertEquals(true, reasons.contains("이미 게시된 활동"))
    }

    // 명함 회수(native-v1.md "명함 교환"): batch가 매일 04:00에 부르는 RecallService. 규칙 자체는 api 모듈의 CardFlowTest·GuestFlowTest가 확인한다
    @Test
    fun `회수는 30일이 지난 비로그인 저장을 빼고 그 안의 것은 남긴다`() {
        jdbc.sql("insert into guest_sessions (digest, created_at) values ('old-session-digest', now()), ('new-session-digest', now())").update()
        jdbc.sql("insert into guest_cards (session_digest, card_id, saved_at) values ('old-session-digest', gen_random_uuid(), now() - interval '31 days'), ('new-session-digest', gen_random_uuid(), now())").update()

        val report = recall.recall()

        assertEquals(RecallService.Report(receipts = 0, guestCards = 1), report)
        assertEquals(listOf("old-session-digest"), jdbc.sql("select session_digest from guest_cards where recalled_at is not null").query(String::class.java).list())
        jdbc.sql("delete from guest_sessions where digest in ('old-session-digest', 'new-session-digest')").update()
    }
}
