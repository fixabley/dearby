package io.wid.dearby.batch

import io.wid.dearby.batch.application.ActivityDiscoverer
import io.wid.dearby.batch.application.ActivityExtractor
import io.wid.dearby.batch.application.Discovery
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.Extraction
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.ModelUsage
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.batch.application.PageFetcher
import io.wid.dearby.batch.job.CollectionJobConfig
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

// 모델·네트워크 대신 가짜 구현으로 잡 전체(설정 읽기 → 대조 → 초안 저장 → 기록)를 확인한다. API 비용이 들지 않는다
// batch 앱은 io.wid.dearby.batch만 스캔하므로 공유 테스트 DB 설정(스키마 비우고 다시 마이그레이션)을 직접 가져온다
@SpringBootTest(properties = ["spring.main.keep-alive=false"])
@Import(TestDatabaseConfig::class)
class CollectionJobTest(
    @Autowired val jobOperator: JobOperator,
    @Autowired val collectActivitiesJob: Job,
    @Autowired val jdbc: JdbcClient,
) {

    @TestConfiguration
    class Fakes {
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
            if (url.endsWith("missing")) throw PageFetchException("HTTP 404")
            FetchedPage(url, "Let'Swift 2026 컨퍼런스 참가 신청은 11월 1일부터 11월 10일까지입니다.", setOf("https://letswift.kr/2026/apply"))
        }

        @Bean
        @Primary
        fun fakeExtractor() = ActivityExtractor { _, _ ->
            Extraction(
                listOf(
                    ExtractedActivity(
                        roundKey = "2026-컨퍼런스", name = "Let'Swift 2026 컨퍼런스", description = "본행사", participationType = "REGISTRATION",
                        applicationUrl = "https://letswift.kr/2026/apply", recruitmentStartAt = "2026-11-01T10:00:00+09:00",
                        recruitmentEndAt = "2026-11-10T23:59:00+09:00", dateLabel = "2026-11-29", schedules = emptyList(),
                        location = null, cost = null, audience = null, evidenceQuote = "참가 신청은 11월 1일부터 11월 10일까지입니다",
                    ),
                    ExtractedActivity(
                        roundKey = "2026-가짜", name = "원문에 없는 활동", description = "", participationType = null,
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
        // V5로 넣은 Let'Swift 활동만 남기고 수집으로 생긴 활동을 지운다(일정은 함께 지워진다)
        jdbc.sql("delete from activities where source_id is distinct from '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
    }

    // Let'Swift 프로그램의 수집 설정: 공식 도메인 letswift.kr 안에서만, intervalDays마다
    private fun officialSetting(intervalDays: Int = 1) = jdbc.sql(
        """
        insert into program_collection_settings (program_id, official_url, allowed_hosts, interval_days, created_at, updated_at)
        select id, 'https://letswift.kr/2026/', array['letswift.kr'], ?, localtimestamp, localtimestamp
        from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'
        """
    ).param(intervalDays).update()

    private fun runCount() = jdbc.sql("select count(*) from collection_runs").query(Int::class.java).single()

    private fun run(date: String) = jobOperator.start(
        collectActivitiesJob,
        JobParametersBuilder().addString(CollectionJobConfig.RUN_DATE, date).addString("trigger", "test-${System.nanoTime()}").toJobParameters(),
    )

    private fun status(name: String) =
        jdbc.sql("select publication_status from activities where name = ?").param(name).query(String::class.java).list()

    @Test
    fun `원문으로 확인된 활동만 초안으로 저장하고, 같은 날은 다시 수집하지 않는다`() {
        officialSetting()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-20").status)

        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(emptyList(), status("원문에 없는 활동"))
        val record = jdbc.sql("select outcome, found_count, saved_count, cardinality(rejected_reasons), input_tokens, web_search_requests, official_only from collection_runs")
            .query { rs, _ -> listOf(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7)) }.single()
        // 페이지 2개 중 1개는 404, 받은 페이지에서 2건을 찾아 1건 저장·1건 제외
        assertEquals(listOf<Any>("SUCCEEDED", 2, 1, 2, 1100L, 1, true), record)
        assertEquals(listOf(true), jdbc.sql("select official_source from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(Boolean::class.java).list())
        val schedules = jdbc.sql("select slot from schedules s join activities a on a.id = s.activity_id where a.name = ? order by slot")
            .param("Let'Swift 2026 컨퍼런스").query(String::class.java).list()
        assertEquals(listOf("ACTIVITY", "APPLICATION"), schedules)

        run("2026-10-20") // 같은 날짜를 다시 실행해도 수집하지 않는다
        assertEquals(1, runCount())
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
    fun `게시된 활동은 다시 수집해도 덮어쓰지 않는다`() {
        officialSetting()
        run("2026-10-21")
        jdbc.sql("update activities set publication_status = 'PUBLISHED', name = '관리자가 고친 이름' where name = ?").param("Let'Swift 2026 컨퍼런스").update()

        run("2026-10-22")

        assertEquals(listOf("PUBLISHED"), status("관리자가 고친 이름"))
        val reasons = jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs where run_date = '2026-10-22'").query(String::class.java).single()
        assertEquals(true, reasons.contains("이미 게시된 활동"))
    }
}
