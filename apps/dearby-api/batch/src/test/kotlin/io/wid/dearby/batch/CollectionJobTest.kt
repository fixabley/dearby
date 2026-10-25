package io.wid.dearby.batch

import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import io.wid.dearby.application.discovery.TextEmbedder
import io.wid.dearby.batch.adaptor.claude.ClaudeCli
import io.wid.dearby.batch.adaptor.claude.CollectionProperties
import io.wid.dearby.batch.adaptor.claude.CollectionPrompts
import io.wid.dearby.batch.application.UrlGuard
import io.wid.dearby.batch.application.StoryBackfillService
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.ExtractedSchedule
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.batch.application.PageFetcher
import io.wid.dearby.batch.application.RenderingPageFetcher
import io.wid.dearby.batch.job.CollectionJobConfig
import io.wid.dearby.batch.push.FavoritePushService
import io.wid.dearby.batch.push.PushMessage
import io.wid.dearby.batch.push.PushResult
import io.wid.dearby.batch.push.PushSender
import io.wid.dearby.domain.push.PushPlatform
import io.wid.dearby.batch.recall.RecallService
import io.wid.dearby.batch.curation.CurationImportService
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 모델·네트워크 대신 가짜 구현으로 잡 전체(설정 읽기 → 도구 서버 → 대조 → 초안 저장 → 기록)를 확인한다. CLI 대신 대본이 실제 도구 서버를 JSON-RPC로 부른다
// batch 앱은 io.wid.dearby.batch만 스캔하므로 공유 테스트 DB 설정(스키마 비우고 다시 마이그레이션)을 직접 가져온다
@SpringBootTest(properties = ["spring.main.keep-alive=false"])
@Import(TestDatabaseConfig::class)
class CollectionJobTest(
    @Autowired val jobOperator: JobOperator,
    @Autowired val collectActivitiesJob: Job,
    @Autowired val jdbc: JdbcClient,
    @Autowired val recall: RecallService,
    @Autowired val json: JsonMapper,
    @Autowired val curationImport: CurationImportService,
    @Autowired val buildDiscoveryQueuesJob: Job,
    @Autowired val stories: StoryBackfillService,
    @Autowired val pushes: FavoritePushService,
) {

    @TestConfiguration
    class Fakes {
        companion object {
            var lastPrompt = "" // 마지막 실행의 요청(운영자 피드백·태그 사전이 들어간다)
            var lastCommand: List<String> = emptyList()
            var lastWorkflow = "" // 마지막 실행에 --system-prompt-file로 넘긴 파일 내용
            fun option(command: List<String>, name: String) = command[command.indexOf(name) + 1]
            var extractedCategory: String? = "CONFERENCE" // 프로그램 기본 종류 테스트에서 null로 바꾼다
            var extractedAudience: String? = null // 지원 자격 확인 테스트에서 원문 대상 구절을 준다
            // 브라우저 렌더링(admin-v1.md): 렌더링 경로로 받은 주소와 HTML 경로로 받은 주소, 브라우저를 내린 횟수
            val renderedUrls = mutableListOf<String>()
            val httpUrls = mutableListOf<String>()
            var rendererClosed = 0
            var extractedTags: List<String> = listOf("창업", "없는 태그") // 태그 테스트: 사전 밖 이름은 버려진다
            var extractedFieldTags: List<Map<String, Any>> = listOf(mapOf("name" to "시험하위분야", "relevance" to 130), mapOf("name" to "없는 분야", "relevance" to 50))
            var extractedSchedules: List<ExtractedSchedule> = emptyList() // 세부 일정 테스트에서 채운다(admin-v1.md "세부 일정 편집기")
            var script: ((String, McpClient) -> Map<String, Any?>)? = null // 테스트가 바꿔 끼우는 CLI 대본. 없으면 defaultScript
            var pageExtra = "" // 장소 테스트에서 받은 페이지 본문 뒤에 붙인다
            var addressResults: List<GeocodeResult> = emptyList() // 가짜 지오코더(네트워크 없음)의 주소 검색 결과
            var embeddingModel = "fake-model" // 가짜 임베딩 모델 id(모델 바꾸기 테스트에서 바꾼다)
            val embeddedTexts = mutableListOf<String>()
            // 가짜 푸시(APNs·FCM 대신): 보낸 (토큰, 알림). 토큰에 gone이 있으면 지운 기기(410·UNREGISTERED)
            val pushed = mutableListOf<Pair<String, PushMessage>>()
            var pushProblem: String? = null

            // claude -p --output-format json 결과 모양
            fun cliResult(subtype: String = "success", turns: Int = 3) = mapOf(
                "type" to "result", "subtype" to subtype, "is_error" to (subtype != "success"), "num_turns" to turns, "total_cost_usd" to 0.0125,
                "usage" to mapOf("input_tokens" to 100, "cache_read_input_tokens" to 1000, "cache_creation_input_tokens" to 0, "output_tokens" to 200, "server_tool_use" to mapOf("web_search_requests" to 1)),
            )
        }

        @Bean
        @Primary
        fun fakePushSender() = object : PushSender {
            override fun problem(platform: PushPlatform) = pushProblem
            override fun send(platform: PushPlatform, token: String, message: PushMessage): PushResult {
                if ("gone" in token) return PushResult.UNREGISTERED
                pushed += token to message
                return PushResult.SENT
            }
        }

        // CLI 프로세스 대신 대본을 돌린다. 도구 서버·도구 처리·서비스의 대조·저장·기록은 실제 코드
        @Bean
        @Primary
        fun fakeCli(properties: CollectionProperties, json: JsonMapper) = object : ClaudeCli(properties, json) {
            override fun execute(command: List<String>, prompt: String, workDir: Path): JsonNode {
                lastCommand = command
                lastPrompt = prompt
                // 도구 없는 한 번 묻기(대기열 글 채우기): 받은 활동마다 같은 글을 답한다
                if ("--mcp-config" !in command) {
                    val items = json.readTree(prompt).toList().map { mapOf("id" to it["id"].asLong(), "hook" to "설명으로 쓴 소개", "highlights" to listOf(mapOf("icon" to "team", "text" to "팀 활동"), mapOf("icon" to "rocket", "text" to "버려질 줄"))) }
                    return json.valueToTree(cliResult() + ("result" to "답입니다: " + json.writeValueAsString(mapOf("items" to items))))
                }
                lastWorkflow = Files.readString(Path.of(option(command, "--system-prompt-file")))
                val mcpUrl = json.readTree(Files.readString(Path.of(option(command, "--mcp-config")))).path("mcpServers").path("dearby").path("url").asString()
                return json.valueToTree((script ?: ::defaultScript)(prompt, McpClient(mcpUrl, json)))
            }
        }

        // 예전 고정 순서를 흉내 낸다: 시작 주소와 찾은 주소(공식 도메인이 있으면 그 안의 없는 페이지, 없으면 웹 전체의 블로그 글)를 받고,
        // 받은 페이지마다 후보 둘(원문에 있는 것·없는 것)을 낸 뒤 끝낸다
        private fun defaultScript(prompt: String, mcp: McpClient): Map<String, Any?> {
            val starts = prompt.lines().firstOrNull { it.startsWith("시작 주소: ") }?.removePrefix("시작 주소: ")?.split(", ").orEmpty()
            val found = if (prompt.contains("허용 도메인: ")) "https://letswift.kr/2026/missing" else "https://blog.example/letswift-2026"
            for (url in (starts + found).distinct()) {
                if (mcp.call("fetch_page", mapOf("url" to url)).first) continue
                candidates().forEach { mcp.call("submit_candidate", it + ("sourceUrl" to url)) }
            }
            mcp.call("finish", mapOf("reason" to "다 냄"))
            return cliResult()
        }

        private fun candidates() = listOf(
            mapOf(
                "roundKey" to "2026-컨퍼런스", "name" to "Let'Swift 2026 컨퍼런스", "nameEn" to "Let'Swift 2026 Conference", "description" to "본행사", "descriptionEn" to "Main event", "participationType" to "REGISTRATION",
                "applicationUrl" to "https://letswift.kr/2026/apply", "recruitmentStartAt" to "2026-11-01T10:00:00+09:00",
                "recruitmentEndAt" to "2026-11-10T23:59:00+09:00", "dateLabel" to "2026-11-29", "startDate" to "2026-11-29", "endDate" to "2026-11-29", "schedules" to extractedSchedules,
                "location" to null, "cost" to null, "audience" to extractedAudience, "evidenceQuote" to "참가 신청은 11월 1일부터 11월 10일까지입니다", "category" to extractedCategory, "tags" to extractedTags, "fieldTags" to extractedFieldTags,
                // 대기열 장면 글: 모르는 icon·너무 긴 글은 그 항목만 버린다
                "hook" to "Swift 개발자가 한자리에", "highlights" to listOf(mapOf("icon" to "talk", "text" to "세션 20개"), mapOf("icon" to "rocket", "text" to "모르는 아이콘"), mapOf("icon" to "ticket", "text" to "가".repeat(41))),
            ),
            mapOf(
                "roundKey" to "2026-가짜", "name" to "원문에 없는 활동", "nameEn" to null, "description" to "", "descriptionEn" to null, "participationType" to null,
                "applicationUrl" to null, "recruitmentStartAt" to null, "recruitmentEndAt" to null, "dateLabel" to "",
                "schedules" to emptyList<Any>(), "location" to null, "cost" to null, "audience" to null, "evidenceQuote" to "원문 어디에도 없는 문장입니다",
            ),
        )


        // 모델 대신 낱말 해시로 만드는 결정적 벡터(native-v1.md "활동 벡터"). 받은 글을 남겨 임베딩 글 규칙과 바뀐 것만 다시 하는지를 본다
        @Bean
        @Primary
        fun fakeEmbedder() = object : TextEmbedder {
            override val modelId: String get() = embeddingModel
            override fun problem(): String? = null
            override fun embed(text: String): FloatArray {
                embeddedTexts += text
                val vector = FloatArray(1024)
                text.split(Regex("\\s+")).forEach { word -> vector[Math.floorMod(word.hashCode(), vector.size)] += 1f }
                val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
                return FloatArray(vector.size) { vector[it] / norm }
            }
        }

        @Bean
        @Primary
        fun fakeFetcher() = PageFetcher { url, allowedHosts ->
            // 실제 받기처럼 허용 호스트 밖은 받지 않는다
            if (!UrlGuard.hostAllowed(URI(url).host, allowedHosts)) throw PageFetchException("허용 호스트 밖: ${URI(url).host}")
            httpUrls += url
            if (url.endsWith("missing")) throw PageFetchException("HTTP 404")
            FetchedPage(url, "Let'Swift 2026 컨퍼런스 참가 신청은 11월 1일부터 11월 10일까지입니다.$pageExtra", setOf("https://letswift.kr/2026/apply"))
        }

        @Bean
        @Primary
        fun fakeGeocoder() = object : Geocoder {
            override fun search(query: String) = addressResults
            override fun searchAddress(query: String) = addressResults
            override fun searchKeyword(query: String) = emptyList<GeocodeResult>()
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

    }

    // 도구 서버를 부르는 쪽(CLI 대신). call은 (오류 여부, 결과 본문)
    class McpClient(val url: String, private val json: JsonMapper) {
        private val http = HttpClient.newHttpClient()
        private var nextId = 0

        fun post(body: Map<String, Any?>, target: String = url): HttpResponse<String> =
            http.send(HttpRequest.newBuilder(URI(target)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString())

        fun rpc(method: String, params: Map<String, Any?> = emptyMap()): JsonNode =
            json.readTree(post(mapOf("jsonrpc" to "2.0", "id" to ++nextId, "method" to method, "params" to params)).body())

        fun call(name: String, arguments: Map<String, Any?>): Pair<Boolean, String> {
            val result = rpc("tools/call", mapOf("name" to name, "arguments" to arguments)).path("result")
            return result.path("isError").asBoolean() to result.path("content").path(0).path("text").asString()
        }
    }

    @BeforeTest
    fun clean() {
        jdbc.sql("delete from program_collection_settings").update()
        jdbc.sql("delete from collection_runs").update()
        jdbc.sql("delete from dismissed_activity_sources").update()
        jdbc.sql("delete from collection_watchlist").update()
        jdbc.sql("delete from collection_feedback").update()
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
        // 분야 태그(native-v1.md "분야·성격 태그와 아이콘"): 상위 > 하위 분야 사전
        val topField = jdbc.sql("insert into tags (name, kind, sort_order) values ('시험상위분야', 'FIELD', 101) returning id").query(Long::class.java).single()
        jdbc.sql("insert into tags (name, kind, parent_id, sort_order) values ('시험하위분야', 'FIELD', ?, 102)").param(topField).update()
        // 이전 기수(V5의 Let'Swift 활동)에 대상 원문·대상자 조건을 두면, 본문에 없던 새 초안이 그 값을 가져오고 inherited_fields에 남긴다
        jdbc.sql("update activities set audience = 'Swift 개발자', eligibility = '{\"op\":\"all\",\"items\":[{\"field\":\"나이\",\"fieldEn\":\"age\",\"from\":19}]}'::jsonb where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-20").status)
        assertEquals(
            listOf("Swift 개발자|19|[\"eligibility\", \"audience\"]"),
            jdbc.sql("select audience || '|' || (eligibility->'items'->0->>'from') || '|' || inherited_fields::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
        assertEquals(1, jdbc.sql("select count(*) from activity_eligibility_terms t join activities a on a.id = t.activity_id where a.name = ?").param("Let'Swift 2026 컨퍼런스").query(Int::class.java).single())
        // 지원 자격 확인(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"): 이전 기수에서 가져온 조건은 이번 원문에서 읽은 것이 아니다
        fun eligibilityChecked() = jdbc.sql("select eligibility_checked from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(Boolean::class.java).single()
        assertEquals(false, eligibilityChecked())

        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(emptyList(), status("원문에 없는 활동"))
        val record = jdbc.sql("select outcome, found_count, saved_count, cardinality(rejected_reasons), input_tokens, web_search_requests, official_only from collection_runs")
            .query { rs, _ -> listOf(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getInt(6), rs.getBoolean(7)) }.single()
        // 페이지 2개 중 1개는 404, 받은 페이지에서 2건을 찾아 1건 저장·1건 제외
        assertEquals(listOf<Any>("SUCCEEDED", 2, 1, 2, 1100L, 1, true), record)
        // 실제로 받은 페이지만 fetched_urls에 남는다(404였던 탐색 주소는 빠진다). 활동 종류는 모델이 뽑은 값 그대로 초안에
        assertEquals("https://letswift.kr/2026/", jdbc.sql("select array_to_string(fetched_urls, ',') from collection_runs").query(String::class.java).single())
        assertEquals(listOf("CONFERENCE"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        assertEquals(
            listOf("""Swift 개발자가 한자리에|[{"icon": "talk", "text": "세션 20개"}]"""),
            jdbc.sql("select hook || '|' || highlights::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
        // 태그(native-v1.md "태그"): 모델이 사전 이름으로 고른 것만 남고(없는 태그는 버림), 추출 요청에는 사전이 종류별로 넘어간다.
        // 분야 태그는 연관도(0~100으로 자름)를 함께 남기고 성격 태그는 연관도가 없다
        assertEquals(
            listOf("창업|null", "시험하위분야|100"),
            jdbc.sql("select t.name || '|' || coalesce(at.relevance::text, 'null') from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ? order by t.sort_order").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
        assertTrue(Fakes.lastPrompt.lines().single { it.startsWith("성격 태그 사전") }.let { it.contains("창업") && it.contains("멘토링") && !it.contains("시험") })
        assertTrue(Fakes.lastPrompt.lines().single { it.startsWith("분야 태그 사전") }.contains("시험상위분야 > 시험하위분야"))
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
        jdbc.sql("update activities set category = 'STUDY', curated_fields = '[\"eligibility\",\"nameEn\",\"category\",\"tags\",\"hook\"]'::jsonb, hook = '운영자 소개', name_en = 'Curated EN', eligibility = '{\"op\":\"all\",\"items\":[{\"field\":\"나이\",\"fieldEn\":\"age\",\"from\":30}]}'::jsonb, description = '옛 설명', audience = '옛 대상', inherited_fields = '[]'::jsonb where name = ?")
            .param("Let'Swift 2026 컨퍼런스").update()
        assertEquals(BatchStatus.COMPLETED, run("2026-10-21").status)
        assertEquals(2, runCount())
        assertEquals(listOf("STUDY"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        // 운영자가 고친 한 줄 소개는 지키고, 고치지 않은 핵심 줄은 수집값으로 갱신한다
        assertEquals(listOf("운영자 소개|1"), jdbc.sql("select hook || '|' || jsonb_array_length(highlights) from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        // 운영자가 지정한 태그(curated tags)는 다시 수집해도 그대로
        assertEquals(listOf("창업", "멘토링", "시험하위분야"), jdbc.sql("select t.name from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ? order by t.sort_order").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        assertEquals(
            listOf("Curated EN|본행사|Swift 개발자|30|[\"audience\"]"),
            jdbc.sql("select name_en || '|' || description || '|' || audience || '|' || (eligibility->'items'->0->>'from') || '|' || inherited_fields::text from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list(),
        )
        // 원문이 대상에 제한이 없다고 밝히면(대상 구절) 조건이 없어도 확인한 것이고, 다음 수집에 구절이 없어도 내리지 않는다
        assertEquals(false, eligibilityChecked())
        try {
            Fakes.extractedAudience = "누구나 참가할 수 있습니다"
            assertEquals(BatchStatus.COMPLETED, run("2026-10-22").status)
            assertEquals(true, eligibilityChecked())
        } finally {
            Fakes.extractedAudience = null
        }
        assertEquals(BatchStatus.COMPLETED, run("2026-10-23").status)
        assertEquals(true, eligibilityChecked())
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
    fun `운영자 피드백 최근 기록은 요청에, 작업 절차는 WORKFLOW_md 파일로 넘기고 그 판을 기록한다`() {
        officialSetting()
        // 기록이 없으면 절 자체가 없다
        run("2026-10-18")
        assertFalse(Fakes.lastPrompt.contains("운영자 피드백"))
        val id = feedback("DATE_CURATED", "2026년 11월 1일~15일", """{"startDate":"2026-11-01","endDate":"2026-11-15"}""", reason = "해커톤 기간")
        run("2026-10-19")
        assertTrue(Fakes.lastPrompt.contains("운영자 피드백(이 프로그램의 최근 기록):\n- #$id [DATE_CURATED] \"2026년 11월 1일~15일\" → {\"endDate\": \"2026-11-15\", \"startDate\": \"2026-11-01\"} (이유: 해커톤 기간)"))
        assertTrue(Fakes.lastPrompt.contains("한도: 페이지 15쪽, 차례 12번"))
        // 작업 절차: 이번 실행 폴더에 꺼낸 jar 안의 WORKFLOW.md. 그 SHA-256 앞 12자리를 agent_log에 남긴다
        val bundled = javaClass.getResourceAsStream("/collector/WORKFLOW.md")!!.readAllBytes()
        assertEquals(String(bundled), Fakes.lastWorkflow)
        assertTrue(Fakes.option(Fakes.lastCommand, "--system-prompt-file").endsWith("/WORKFLOW.md"))
        assertEquals(sha12(bundled), jdbc.sql("select agent_log ->> 'workflowVersion' from collection_runs where run_date = '2026-10-19'").query(String::class.java).single())
        assertEquals("[$id]", jdbc.sql("select agent_log -> 'feedbackInjected' from collection_runs where run_date = '2026-10-19'").query(String::class.java).single())
    }

    private fun sha12(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).take(12)

    // Let'Swift 프로그램(또는 programId)의 피드백 한 행
    private fun feedback(kind: String, source: String, corrected: String = "{}", reason: String? = null, host: String? = null, programId: Long? = letswift()): Long = jdbc.sql(
        "insert into collection_feedback (kind, program_id, host, source_text, corrected_value, reason, created_at) values (?, ?, ?, ?, ?::jsonb, ?, now()) returning id"
    ).param(kind).param(programId).param(host).param(source).param(corrected).param(reason).query(Long::class.java).single()

    private fun letswift() = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()

    @Test
    fun `모델이 종류를 못 뽑으면 프로그램 기본 종류를 초안에 쓴다`() {
        officialSetting()
        jdbc.sql("update programs set category = 'MEETUP' where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").update()
        // 태그도 같다: 모델이 하나도 못 고르면 프로그램 태그를 기본으로
        jdbc.sql("insert into program_tags (program_id, tag_id) select p.id, t.id from programs p, tags t where p.source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db' and t.name = '협업'").update()
        Fakes.extractedCategory = null
        Fakes.extractedTags = emptyList()
        Fakes.extractedFieldTags = emptyList()
        try {
            assertEquals(BatchStatus.COMPLETED, run("2026-10-22").status)
            assertEquals(listOf("MEETUP"), jdbc.sql("select category from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
            assertEquals(listOf("협업"), jdbc.sql("select t.name from activity_tags at join tags t on t.id = at.tag_id join activities a on a.id = at.activity_id where a.name = ?").param("Let'Swift 2026 컨퍼런스").query(String::class.java).list())
        } finally {
            Fakes.extractedCategory = "CONFERENCE"
            Fakes.extractedFieldTags = listOf(mapOf("name" to "시험하위분야", "relevance" to 130), mapOf("name" to "없는 분야", "relevance" to 50))
            Fakes.extractedTags = listOf("창업", "없는 태그")
            jdbc.sql("delete from program_tags").update()
            jdbc.sql("delete from tags where name in ('시험상위분야', '시험하위분야')").update()
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
    fun `운영자가 지운 세부 일정은 다시 수집해도 되살아나지 않고, 운영자가 고친 일정과 같은 수집 일정은 다시 넣지 않는다`() {
        officialSetting()
        Fakes.extractedSchedules = listOf(
            ExtractedSchedule(title = "1차 면접", kind = "INTERVIEW", startAt = "2026-11-15T10:00:00+09:00", endAt = "2026-11-15T12:00:00+09:00", dateLabel = "11월 15일 면접", recurrence = "FREQ=DAILY;COUNT=2"),
            ExtractedSchedule(title = "결과 발표", kind = "ANNOUNCEMENT", startAt = null, endAt = null, dateLabel = "11월 20일 발표", startDate = "2026-11-20", endDate = "2026-11-20"),
        )
        try {
            assertEquals(BatchStatus.COMPLETED, run("2026-10-20").status)
            val activityId = jdbc.sql("select id from activities where name = ?").param("Let'Swift 2026 컨퍼런스").query(Long::class.java).single()
            assertEquals(listOf("1차 면접", "결과 발표"), detailTitles(activityId))
            // 시각 세부 일정의 반복 규칙은 그대로 저장한다(native-v1.md "반복 일정과 겹침 결과 묶음")
            assertEquals(listOf("FREQ=DAILY;COUNT=2"), jdbc.sql("select recurrence from schedules where activity_id = ? and title = '1차 면접'").param(activityId).query(String::class.java).list())
            // 모집·활동 기간 행은 다시 수집해도 제자리에서 고쳐 id가 그대로다(일정 상태·사용자 선택·겹침 확인 시간이 id에 묶임, native-v1.md "내 활동 서버 저장")
            fun slotIds() = jdbc.sql("select id from schedules where activity_id = ? and slot is not null order by slot").param(activityId).query(Long::class.java).list()
            val slotsBefore = slotIds()
            jdbc.sql("update schedules set overlap_start_time = '10:00', overlap_end_time = '17:00' where activity_id = ? and slot = 'ACTIVITY'").param(activityId).update()
            // 어드민이 1차 면접을 지우고(curated_deleted) 결과 발표에 사유를 달아 고쳤다(curated)
            jdbc.sql("update schedules set curated_deleted = true where activity_id = ? and title = '1차 면접'").param(activityId).update()
            jdbc.sql("update schedules set curated = true, curated_note = '공식 일정표 확인' where activity_id = ? and title = '결과 발표'").param(activityId).update()
            assertEquals(BatchStatus.COMPLETED, run("2026-10-21").status)
            assertEquals(slotsBefore, slotIds())
            assertEquals(1, jdbc.sql("select count(*) from schedules where activity_id = ? and slot = 'ACTIVITY' and overlap_start_time = '10:00'").param(activityId).query(Int::class.java).single())
            // 보이는 세부 일정은 운영자가 고친 결과 발표 하나뿐이고(수집된 같은 일정은 다시 넣지 않음), 지운 1차 면접은 표시된 행으로만 남는다
            assertEquals(listOf("결과 발표"), detailTitles(activityId))
            assertEquals(listOf("공식 일정표 확인"), jdbc.sql("select curated_note from schedules where activity_id = ? and title = '결과 발표'").param(activityId).query(String::class.java).list())
            assertEquals(1, jdbc.sql("select count(*) from schedules where activity_id = ? and title = '1차 면접' and curated_deleted").param(activityId).query(Int::class.java).single())
        } finally {
            Fakes.extractedSchedules = emptyList()
        }
    }

    // 응답·공개 API에 보이는 세부 일정 제목(지운 일정 제외). 정렬은 DB 콜레이션에 따르지 않게 코틀린에서
    private fun detailTitles(activityId: Long) =
        jdbc.sql("select title from schedules where activity_id = ? and slot is null and not curated_deleted").param(activityId).query(String::class.java).list().sortedBy { it.toString() }

    @Test
    fun `게시된 활동은 다시 수집해도 덮어쓰지 않는다`() {
        officialSetting()
        run("2026-10-21")
        // 게시 뒤 대기열 글이 비어 있고 핵심 줄은 운영자 지정이면, 다시 수집할 때 한 줄 소개만 채운다(다른 필드는 그대로)
        jdbc.sql("update activities set publication_status = 'PUBLISHED', name = '관리자가 고친 이름', hook = null, highlights = null, curated_fields = '[\"highlights\"]'::jsonb where name = ?").param("Let'Swift 2026 컨퍼런스").update()

        run("2026-10-22")

        assertEquals(listOf("PUBLISHED"), status("관리자가 고친 이름"))
        fun story() = jdbc.sql("select coalesce(hook, '-') || '|' || coalesce(highlights::text, '-') from activities where name = '관리자가 고친 이름'").query(String::class.java).single()
        assertEquals("Swift 개발자가 한자리에|-", story())
        // 이미 있는 한 줄 소개는 덮어쓰지 않는다
        jdbc.sql("update activities set hook = '운영 소개' where name = '관리자가 고친 이름'").update()
        run("2026-10-23")
        assertEquals("운영 소개|-", story())
        val reasons = jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs where run_date = '2026-10-22'").query(String::class.java).single()
        assertEquals(true, reasons.contains("이미 게시된 활동"))
    }

    // 에이전트 루프: 대본이 CLI 대신 실제 도구 서버를 부른다. 원문 대조·저장·기록은 서비스의 실제 코드
    private fun scripted(date: String, script: (McpClient) -> Map<String, Any?>) {
        Fakes.script = { _, mcp -> script(mcp) }
        try {
            assertEquals(BatchStatus.COMPLETED, run(date).status)
        } finally {
            Fakes.script = null
        }
    }

    private fun candidate(quote: String, sourceUrl: String = "https://letswift.kr/2026/") = mapOf(
        "roundKey" to "2026-컨퍼런스", "name" to "Let'Swift 2026 컨퍼런스", "description" to "본행사", "participationType" to "REGISTRATION",
        "dateLabel" to "", "schedules" to emptyList<Any>(), "evidenceQuote" to quote, "sourceUrl" to sourceUrl,
    )

    private fun place() = jdbc.sql("select coalesce(address_line1, '-') || '|' || coalesce(address_line2, '-') from schedules s join activities a on a.id = s.activity_id where a.name = ? and slot = 'ACTIVITY'")
        .param("Let'Swift 2026 컨퍼런스").query(String::class.java).single()

    @Test
    fun `수집 장소는 지오코딩이 확실할 때만 활동 기간 행에 채우고, 재수집은 지우지 않으며 운영자가 지운 장소는 다시 채우지 않는다`() {
        officialSetting()
        Fakes.pageExtra = " 장소: 코엑스 그랜드볼룸 (서울 강남구 영동대로 513)"
        val coex = mapOf("name" to "코엑스 그랜드볼룸", "address" to "서울 강남구 영동대로 513", "online" to false, "evidenceQuote" to "장소: 코엑스 그랜드볼룸")
        fun submitWith(place: Map<String, Any?>?) = { mcp: McpClient ->
            mcp.call("fetch_page", mapOf("url" to "https://letswift.kr/2026/"))
            mcp.call("submit_candidate", candidate("참가 신청은 11월 1일부터 11월 10일까지입니다") + ("place" to place))
            mcp.call("finish", mapOf("reason" to "끝"))
            Fakes.cliResult()
        }
        try {
            // 주소 결과가 둘이면(키워드도 없음) 확실하지 않아 장소를 두지 않는다
            Fakes.addressResults = listOf(GeocodeResult("코엑스", "서울 강남구 영동대로 513", 37.51, 127.06, "fake"), GeocodeResult("코엑스몰", "서울 강남구 영동대로 511", 37.5, 127.05, "fake"))
            scripted("2026-10-20", submitWith(coex))
            assertEquals("-|-", place())
            // 주소 결과가 하나면 이름은 제출한 것, 주소는 제공자 값
            Fakes.addressResults = Fakes.addressResults.take(1)
            scripted("2026-10-21", submitWith(coex))
            assertEquals("코엑스 그랜드볼룸|서울 강남구 영동대로 513", place())
            // 온라인·장소 없음으로 다시 수집해도 있던 장소는 남는다
            scripted("2026-10-22", submitWith(mapOf("name" to null, "address" to null, "online" to true, "evidenceQuote" to null)))
            assertEquals("코엑스 그랜드볼룸|서울 강남구 영동대로 513", place())
            // 운영자가 장소를 지우면(curated place) 수집이 다시 채우지 않는다
            jdbc.sql("update schedules set address_line1 = null, address_line2 = null, lat = null, lng = null where slot = 'ACTIVITY' and activity_id = (select id from activities where name = ?)").param("Let'Swift 2026 컨퍼런스").update()
            jdbc.sql("update activities set curated_fields = '[\"place\"]'::jsonb where name = ?").param("Let'Swift 2026 컨퍼런스").update()
            scripted("2026-10-23", submitWith(coex))
            assertEquals("-|-", place())
        } finally {
            Fakes.pageExtra = ""
            Fakes.addressResults = emptyList()
        }
    }

    private fun agentLog(path: String) = jdbc.sql("select agent_log #>> '{$path}' from collection_runs").query(String::class.java).single()

    @Test
    fun `에이전트는 허용 밖 주소를 도구 오류로 받고, 거절된 후보를 고쳐 다시 내면 저장하고 finish로 끝낸다`() {
        officialSetting()
        val results = mutableListOf<Pair<Boolean, String>>()
        scripted("2026-10-20") { mcp ->
            // 도구 서버: 127.0.0.1의 비밀 경로로만 받고, 도구 다섯을 알린다
            assertTrue(mcp.url.startsWith("http://127.0.0.1:"))
            assertEquals<Int>(404, mcp.post(mapOf("jsonrpc" to "2.0", "id" to 0, "method" to "tools/list"), mcp.url.substringBeforeLast('/') + "/wrong").statusCode())
            assertEquals("dearby", mcp.rpc("initialize", mapOf("protocolVersion" to "2025-06-18")).path("result").path("serverInfo").path("name").asString())
            assertEquals<Int>(202, mcp.post(mapOf("jsonrpc" to "2.0", "method" to "notifications/initialized")).statusCode())
            assertEquals(listOf("fetch_page", "render_page", "submit_candidate", "search_feedback", "finish"), (mcp.rpc("tools/list").path("result").path("tools") as Iterable<JsonNode>).map { it.path("name").asString() })
            results += mcp.call("fetch_page", mapOf("url" to "https://evil.example/letswift"))
            results += mcp.call("fetch_page", mapOf("url" to "https://letswift.kr/2026/"))
            results += mcp.call("submit_candidate", candidate("원문 어디에도 없는 문장입니다"))
            results += mcp.call("submit_candidate", candidate("참가 신청은 11월 1일부터 11월 10일까지입니다"))
            results += mcp.call("finish", mapOf("reason" to "다 냄"))
            Fakes.cliResult(turns = 5)
        }
        // 허용 호스트 밖은 받지 않고 오류로, 공식 페이지는 본문과 링크를 받는다
        assertEquals(true to "받을 수 없음: 허용 호스트 밖: evil.example", results[0])
        assertTrue(!results[1].first && results[1].second.contains("<page>") && results[1].second.contains("https://letswift.kr/2026/apply"))
        // 원문에 없는 근거 구절은 거절 이유와 함께 돌려주고, 고쳐 낸 후보는 저장된다
        assertEquals(listOf(true to "거절됨: 근거 구절이 원문에 없음", false to "저장됨: 초안으로 저장됨", false to "끝"), results.drop(2))
        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))

        val record = jdbc.sql("select outcome, found_count, saved_count, input_tokens, web_search_requests, array_to_string(fetched_urls, ','), array_to_string(rejected_reasons, '|') from collection_runs")
            .query { rs, _ -> listOf(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4), rs.getInt(5), rs.getString(6), rs.getString(7)) }.single()
        // 입력 토큰은 캐시 읽기를 포함한 합
        assertEquals(listOf<Any>("SUCCEEDED", 2, 1, 1100L, 1, "https://letswift.kr/2026/"), record.take(6))
        assertEquals("https://evil.example/letswift: 허용 호스트 밖: evil.example|Let'Swift 2026 컨퍼런스: 근거 구절이 원문에 없음", record[6])
        assertEquals(listOf("finish: 다 냄", "false", "5", "2", "1000", "0.0125", "5"), listOf("stopReason", "capReached", "turns", "pages", "usage,cacheReadInputTokens", "costUsd").map(::agentLog) +
            jdbc.sql("select jsonb_array_length(agent_log->'calls')::text from collection_runs").query(String::class.java).single())
    }

    @Test
    fun `에이전트는 첫 제출에 바로 저장하고, 페이지 한도 밖은 도구 오류로, 턴 한도에 닿으면 멈춘 이유를 남긴다`() {
        officialSetting()
        val results = mutableListOf<Pair<Boolean, String>>()
        scripted("2026-10-21") { mcp ->
            results += mcp.call("fetch_page", mapOf("url" to "https://letswift.kr/2026/"))
            results += mcp.call("submit_candidate", candidate("참가 신청은 11월 1일부터 11월 10일까지입니다"))
            // 기본 한도 15쪽: 14번 더 받으면 16번째는 받지 않는다
            repeat(14) { mcp.call("fetch_page", mapOf("url" to "https://letswift.kr/2026/")) }
            results += mcp.call("render_page", mapOf("url" to "https://letswift.kr/2026/"))
            Fakes.cliResult(subtype = "error_max_turns", turns = 12)
        }
        assertEquals(false to "저장됨: 초안으로 저장됨", results[1])
        assertEquals(true to "페이지 한도(15)에 닿아 더 받을 수 없음. 받은 페이지로 제출하고 finish하라", results[2])
        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(listOf("턴 한도(12)", "true", "15"), listOf("stopReason", "capReached", "pages").map(::agentLog))
        assertEquals("SUCCEEDED|1|수집 중단: 턴 한도(12)", jdbc.sql("select outcome || '|' || saved_count || '|' || array_to_string(rejected_reasons, '|') from collection_runs").query(String::class.java).single())
    }

    @Test
    fun `CLI는 작업 절차 파일·이번 실행의 도구 서버·웹 검색만 쓰고, 운영 파일이 있으면 그 파일을 쓴다`() {
        // 운영에서 내려 둔 파일(COLLECTION_WORKFLOW_PATH)이 있으면 그것, 없거나 폴더면 jar 안의 기본 파일
        val mounted = Files.createTempFile("workflow-", ".md").also { Files.writeString(it, "# 운영 절차") }
        val missing = mounted.resolveSibling("없는-WORKFLOW.md")
        fun runWith(path: String?): Pair<List<String>, ClaudeCli.Run> {
            var captured = emptyList<String>()
            var mcpConfig = "" to ""
            val cli = object : ClaudeCli(CollectionProperties(workflowPath = path), json) {
                override fun execute(command: List<String>, prompt: String, workDir: Path): JsonNode {
                    captured = command
                    // 도구 서버 주소는 인자가 아니라 소유자만 읽는 파일로 넘긴다
                    val config = Path.of(Fakes.option(command, "--mcp-config"))
                    mcpConfig = Files.readString(config) to PosixFilePermissions.toString(Files.getPosixFilePermissions(config))
                    return json.valueToTree(Fakes.cliResult())
                }
            }
            val run = cli.run("요청", "http://127.0.0.1:1234/secret")
            assertEquals("""{"mcpServers":{"dearby":{"type":"http","url":"http://127.0.0.1:1234/secret"}}}""" to "rw-------", mcpConfig)
            assertFalse(captured.any { it.contains("secret") })
            return captured to run
        }
        try {
            val (command, run) = runWith(mounted.toString())
            fun option(name: String) = Fakes.option(command, name)
            assertEquals(mounted.toString(), option("--system-prompt-file"))
            assertEquals(sha12(Files.readAllBytes(mounted)), run.workflowVersion)
            assertEquals("1.00", option("--max-budget-usd"))
            assertTrue("--strict-mcp-config" in command)
            assertEquals("WebSearch", option("--tools"))
            assertEquals("WebSearch,mcp__dearby__fetch_page,mcp__dearby__render_page,mcp__dearby__submit_candidate,mcp__dearby__search_feedback,mcp__dearby__finish", option("--allowedTools"))
            assertEquals("12", option("--max-turns"))
            val bundled = sha12(javaClass.getResourceAsStream("/collector/WORKFLOW.md")!!.readAllBytes())
            for (fallback in listOf(missing.toString(), mounted.parent.toString(), null)) {
                val (other, otherRun) = runWith(fallback)
                val file = Fakes.option(other, "--system-prompt-file")
                assertTrue(file.endsWith("/WORKFLOW.md") && file.contains("dearby-claude-"), file)
                assertEquals(bundled, otherRun.workflowVersion)
            }
        } finally {
            Files.deleteIfExists(mounted)
        }
    }

    @Test
    fun `search_feedback은 같은 프로그램, 같은 호스트, 다른 프로그램 순으로 찾고 찾은 기록을 남긴다`() {
        officialSetting()
        // 프로그램이 지워진 기록(program_id null)은 호스트로만 순위가 정해진다
        val global = feedback("DATE_CURATED", "2026년 11월 29일 토요일 오후", programId = null, host = "other.example")
        val sameHost = feedback("DATE_CURATED", "2026년 11월 29일 토요일 오전", programId = null, host = "letswift.kr")
        val sameProgram = feedback("DATE_CURATED", "2026년 11월 29일 (토)")
        feedback("DATE_CURATED", "전혀 관계없는 문구입니다", programId = null)
        val excluded = feedback("WATCH_EXCLUDE", "2026년 11월 29일 토요일")
        var found = ""
        var onlyDates = ""
        scripted("2026-10-20") { mcp ->
            found = mcp.call("search_feedback", mapOf("text" to "2026년 11월 29일 토요일")).second
            onlyDates = mcp.call("search_feedback", mapOf("text" to "2026년 11월 29일 토요일", "kind" to "DATE_CURATED")).second
            mcp.call("finish", mapOf("reason" to "끝"))
            Fakes.cliResult()
        }
        fun ids(text: String) = Regex("#(\\d+)").findAll(text).map { it.groupValues[1].toLong() }.toList()
        assertEquals(listOf(excluded, sameProgram, sameHost, global), ids(found))
        assertEquals(listOf(sameProgram, sameHost, global), ids(onlyDates))
        assertEquals(json.writeValueAsString(listOf(excluded, sameProgram, sameHost, global)).replace(",", ", "), jdbc.sql("select agent_log -> 'feedbackRetrieved' from collection_runs").query(String::class.java).single())
    }

    @Test
    fun `운영자가 지운 초안과 같은 이름은 피드백으로 거절하고, 운영자가 날짜를 지정한 문구와 해석이 다르면 주의를 붙여 저장한다`() {
        officialSetting()
        val dismissed = feedback("DRAFT_DISMISSED", "Let'Swift  2026 워크숍", reason = "중복")
        val curated = feedback("DATE_CURATED", "11월 29일(토)", """{"slot":"ACTIVITY","startDate":"2026-11-28","endDate":"2026-11-29"}""", reason = "전야제 포함")
        val results = mutableListOf<Pair<Boolean, String>>()
        scripted("2026-10-20") { mcp ->
            mcp.call("fetch_page", mapOf("url" to "https://letswift.kr/2026/"))
            results += mcp.call("submit_candidate", candidate("참가 신청은 11월 1일부터 11월 10일까지입니다") + mapOf("name" to "let'swift 2026 워크숍", "roundKey" to "2026-워크숍"))
            results += mcp.call("submit_candidate", candidate("참가 신청은 11월 1일부터 11월 10일까지입니다") + mapOf("dateLabel" to "11월 29일(토)", "startDate" to "2026-11-29", "endDate" to "2026-11-29"))
            mcp.call("finish", mapOf("reason" to "끝"))
            Fakes.cliResult()
        }
        assertEquals(true to "거절됨: 운영자 피드백 #$dismissed(DRAFT_DISMISSED): \"Let'Swift  2026 워크숍\"(이유: 중복)(다시 제출하지 않는다)", results[0])
        assertEquals(false, results[1].first)
        assertEquals(
            "저장됨: 초안으로 저장됨\n주의(운영자 피드백 #$curated): \"11월 29일(토)\"를 운영자는 startDate 2026-11-28, endDate 2026-11-29로 지정했다(이유: 전야제 포함). 제출한 \"11월 29일(토)\" 해석은 startDate 2026-11-29, endDate 2026-11-29",
            results[1].second,
        )
        assertEquals(listOf("DRAFT"), status("Let'Swift 2026 컨퍼런스"))
        assertEquals(emptyList(), status("let'swift 2026 워크숍"))
        assertTrue(jdbc.sql("select array_to_string(rejected_reasons, '|') from collection_runs").query(String::class.java).single().contains("운영자 피드백 #$dismissed"))
        assertEquals("[$dismissed, $curated]", jdbc.sql("select agent_log -> 'feedbackApplied' from collection_runs").query(String::class.java).single())
    }

    @Test
    fun `피드백 채우기는 운영자가 지정한 일정·필드와 조심 목록을 옮기고, 다시 실행해도 같은 행을 두 번 넣지 않는다`() {
        val program = letswift()
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at, curated, curated_note)
            select id, null, '해커톤', 'EVENT', 'CONFIRMED', 'ALL_DAY', date '2026-11-01', date '2026-11-16', '2026년 11월 1일~15일', 99, localtimestamp, localtimestamp, true, '해커톤 기간'
            from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'
            """
        ).update()
        val (fields, category) = jdbc.sql("select curated_fields::text, category from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query { rs, _ -> rs.getString(1) to rs.getString(2) }.single()
        jdbc.sql("update activities set curated_fields = '[\"category\",\"description\"]'::jsonb, category = 'CONFERENCE' where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()
        jdbc.sql("insert into collection_watchlist (program_id, name, normalized_name, kind, reason, created_at) values (?, '다른 과정', '다른과정', 'EXCLUDE', '다른 프로그램', now())").param(program).update()
        val backfill = String(javaClass.getResourceAsStream("/db/migration/V44__collection_feedback_backfill.sql")!!.readAllBytes())
        try {
            repeat(2) { jdbc.sql(backfill).update() }
            assertEquals(
                listOf(
                    "DATE_CURATED|letswift.kr|2026년 11월 1일~15일|2026-11-01|2026-11-15|해커톤 기간",
                    "FIELD_CURATED|letswift.kr|${activityName()}|category|CONFERENCE|",
                    "WATCH_EXCLUDE||다른 과정|||다른 프로그램",
                ),
                jdbc.sql(
                    """
                    select kind || '|' || coalesce(host, '') || '|' || source_text || '|' ||
                           coalesce(corrected_value ->> 'startDate', corrected_value ->> 'field', '') || '|' || coalesce(corrected_value ->> 'endDate', corrected_value ->> 'to', '') || '|' || coalesce(reason, '')
                    from collection_feedback where program_id = ? order by kind
                    """
                ).param(program).query(String::class.java).list(),
            )
        } finally {
            jdbc.sql("update activities set curated_fields = ?::jsonb, category = ? where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").param(fields).param(category).update()
        }
    }

    // 운영 데이터 가져오기(admin-v1.md "운영 데이터 가져오기 — 2026-10-22"): dry run은 쓰지 않고, apply는 빈 것만 운영자 지정 없이 채운다
    @Test
    fun `운영 데이터 가져오기는 dry run에서 쓰지 않고 apply에서 빈 조건·태그·장소·로고만 채운다`() {
        val source = "'01b773dc-5e13-4d14-a182-9642b47d596e'"
        val activityId = jdbc.sql("select id from activities where source_id = $source").query(Long::class.java).single()
        val (programId, organizationId) = jdbc.sql("select p.id, p.organization_id from activities a join programs p on p.id = a.program_id where a.source_id = $source").query { rs, _ -> rs.getLong(1) to rs.getLong(2) }.single()
        val saved = jdbc.sql("select coalesce(eligibility::text, ''), curated_fields::text from activities where id = ?").param(activityId).query { rs, _ -> rs.getString(1) to rs.getString(2) }.single()
        val dir = Files.createTempDirectory("curation")
        Files.write(dir.resolve("org.png"), byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3))
        Files.write(dir.resolve("cover.webp"), byteArrayOf(0x52, 0x49, 0x46, 0x46, 9))
        val file = dir.resolve("curation.json")
        Files.writeString(file, json.writeValueAsString(mapOf(
            "activities" to listOf(mapOf(
                "id" to activityId,
                "eligibility" to mapOf("op" to "all", "items" to listOf(mapOf("field" to "나이", "fieldEn" to "age", "from" to 19))),
                "tagNames" to listOf("멘토링", "없는태그", "반도체"), "tagNameEn" to mapOf("반도체" to "Semiconductor"), "tagKind" to mapOf("반도체" to "FIELD"),
                "place" to mapOf("name" to "코엑스", "address" to "서울 강남구 영동대로 513", "lat" to 37.5115, "lng" to 127.0595),
            )),
            "programs" to listOf(mapOf(
                "id" to programId, "tagNames" to listOf("시험공학", "반도체", "네트워킹"),
                "tagNameEn" to mapOf("시험공학" to "testEngineering", "반도체" to "Semiconductor"), "tagKind" to mapOf("시험공학" to "FIELD"), "tagParent" to mapOf("반도체" to "시험공학"),
            )),
            "organizationLogos" to listOf(mapOf("organizationId" to organizationId, "file" to "org.png")),
            "programCovers" to listOf(mapOf("programId" to programId, "file" to "cover.webp", "sourcePage" to "https://letswift.kr/2026/", "sourceImage" to "https://letswift.kr/2026/og.webp")),
        )))
        fun eligibility() = jdbc.sql("select coalesce(eligibility->'items'->0->>'from', '') from activities where id = ?").param(activityId).query(String::class.java).single()
        fun activityTags() = jdbc.sql("select t.name from activity_tags x join tags t on t.id = x.tag_id where x.activity_id = ? order by t.name").param(activityId).query(String::class.java).list()
        fun place() = jdbc.sql("select coalesce(address_line1, '') from schedules where activity_id = ? and slot = 'ACTIVITY'").param(activityId).query(String::class.java).list()
        fun logoCount() = jdbc.sql("select count(*) from organization_logos where organization_id = ?").param(organizationId).query(Int::class.java).single()
        try {
            jdbc.sql("update activities set eligibility = null, curated_fields = '[]'::jsonb where id = ?").param(activityId).update()
            jdbc.sql("delete from activity_tags where activity_id = ?").param(activityId).update()
            jdbc.sql("delete from program_tags where program_id = ?").param(programId).update()
            jdbc.sql("update schedules set address_line1 = null, address_line2 = null, lat = null, lng = null where activity_id = ? and slot = 'ACTIVITY'").param(activityId).update()

            // dry run: 계획만 나오고 아무것도 바뀌지 않는다(새 태그도 만들지 않는다)
            val plan = curationImport.import(file, apply = false)
            assertEquals(listOf("eligibility", "tags", "place", "tags", "logo", "cover"), plan.filter { it.action == "SET" }.map { it.field })
            assertEquals(listOf("사전에 없음, 영문 이름 없음: 없는태그"), plan.filter { it.action == "SKIP" }.map { it.detail })
            assertEquals("", eligibility()); assertEquals(emptyList(), activityTags()); assertEquals(0, logoCount())
            assertEquals(0, jdbc.sql("select count(*) from tags where name = '반도체'").query(Int::class.java).single())

            // apply: 빈 것을 채우고, 검색용 조건도 펼치며, 운영자 지정 필드에는 남기지 않는다
            curationImport.import(file, apply = true)
            assertEquals("19", eligibility())
            assertEquals(1, jdbc.sql("select count(*) from activity_eligibility_terms where activity_id = ?").param(activityId).query(Int::class.java).single())
            assertEquals(listOf("멘토링", "반도체"), activityTags())
            assertEquals(listOf("코엑스"), place())
            // 프로그램 태그는 보낸 순서가 position(사전 순과 다르다). 새 태그는 tagKind로 종류를, tagParent로 상위 분야를 단다
            assertEquals(
                listOf("시험공학|FIELD|", "반도체|FIELD|시험공학", "네트워킹|TRAIT|"),
                jdbc.sql("select t.name || '|' || t.kind || '|' || coalesce(p.name, '') from program_tags x join tags t on t.id = x.tag_id left join tags p on p.id = t.parent_id where x.program_id = ? order by x.position").param(programId).query(String::class.java).list(),
            )
            assertEquals(1, logoCount())
            assertEquals(
                listOf("image/webp|https://letswift.kr/2026/|https://letswift.kr/2026/og.webp|true"),
                jdbc.sql("select content_type || '|' || source_page || '|' || source_image || '|' || enabled from program_covers where program_id = ?").param(programId).query(String::class.java).list(),
            )
            assertEquals("[]", jdbc.sql("select curated_fields::text from activities where id = ?").param(activityId).query(String::class.java).single())

            // 이미 채워진 것은 다시 실행해도 건너뛰고, 운영자 지정 필드는 overwrite여도 건드리지 않는다
            assertEquals(emptyList(), curationImport.import(file, apply = true).filter { it.action == "SET" })
            jdbc.sql("update activities set curated_fields = '[\"tags\"]'::jsonb where id = ?").param(activityId).update()
            Files.writeString(file, json.writeValueAsString(mapOf("activities" to listOf(mapOf("id" to activityId, "overwrite" to true, "tagNames" to listOf("창업"))))))
            assertEquals(listOf("운영자 지정"), curationImport.import(file, apply = true).map { it.detail })
            assertEquals(listOf("멘토링", "반도체"), activityTags())
        } finally {
            jdbc.sql("update activities set eligibility = nullif(?, '')::jsonb, curated_fields = ?::jsonb where id = ?").param(saved.first).param(saved.second).param(activityId).update()
            jdbc.sql("delete from activity_eligibility_terms where activity_id = ?").param(activityId).update()
            jdbc.sql("delete from activity_tags where activity_id = ?").param(activityId).update()
            jdbc.sql("delete from program_tags where program_id = ?").param(programId).update()
            jdbc.sql("delete from tags where name in ('반도체', '시험공학')").update()
            jdbc.sql("delete from organization_logos where organization_id = ?").param(organizationId).update()
            jdbc.sql("delete from program_covers where program_id = ?").param(programId).update()
            jdbc.sql("update schedules set address_line1 = null, address_line2 = null, lat = null, lng = null where activity_id = ? and slot = 'ACTIVITY'").param(activityId).update()
        }
    }

    @Test
    fun `운영 데이터 가져오기는 조직·프로그램·수집 설정을 만들고 초안을 게시하거나 지운다`() {
        val source = "'01b773dc-5e13-4d14-a182-9642b47d596e'"
        val (activityId, organizationId) = jdbc.sql("select a.id, p.organization_id from activities a join programs p on p.id = a.program_id where a.source_id = $source").query { rs, _ -> rs.getLong(1) to rs.getLong(2) }.single()
        val (organizationName, organizationParent) = jdbc.sql("select name, parent_organization_id from organizations where id = ?").param(organizationId).query { rs, _ -> rs.getString(1) to rs.getObject(2) }.single()
        val status = jdbc.sql("select publication_status from activities where id = ?").param(activityId).query(String::class.java).single()
        // 지울 초안: 기존 수집 활동을 출처만 바꿔 복사한다
        val columns = jdbc.sql("select string_agg(column_name, ', ') from information_schema.columns where table_name = 'activities' and column_name not in ('id', 'source_id')").query(String::class.java).single()
        val draftSource = "a5a5a5a5-0000-4000-8000-000000000001"
        val draftId = jdbc.sql("insert into activities (source_id, $columns) select '$draftSource'::uuid, $columns from activities where id = ? returning id").param(activityId).query(Long::class.java).single()
        jdbc.sql("update activities set publication_status = 'DRAFT', name = '가져오기 지울 초안' where id = ?").param(draftId).update()
        val scheduleColumns = jdbc.sql("select string_agg(column_name, ', ') from information_schema.columns where table_name = 'schedules' and column_name not in ('id', 'activity_id', 'source_id')").query(String::class.java).single()
        jdbc.sql("insert into schedules (activity_id, $scheduleColumns) select ?, $scheduleColumns from schedules where activity_id = ?").param(draftId).param(activityId).update()
        val file = Files.createTempDirectory("catalog").resolve("curation.json")
        Files.writeString(file, json.writeValueAsString(mapOf(
            "organizations" to listOf(
                mapOf("key" to "same", "name" to " $organizationName ", "parentId" to organizationParent), // 같은 상위 아래 같은 이름이 있으면 그것을 쓴다
                mapOf("key" to "club", "name" to "가져오기 연합동아리"),
                mapOf("key" to "child", "name" to "가져오기 동아리", "nameEn" to "Import Club", "parentKey" to "club"),
            ),
            "newPrograms" to listOf(
                mapOf("key" to "p", "name" to "가져오기 동아리 모집", "organizationKey" to "child", "category" to "CLUB",
                    "collection" to mapOf("officialUrl" to "https://club.example.org/recruit", "allowedHosts" to listOf("Club.Example.org"), "enabled" to true)),
                mapOf("key" to "dup", "name" to "가져오기 동아리 모집", "organizationKey" to "child"),
            ),
            "activities" to listOf(mapOf("id" to draftId, "dismiss" to true), mapOf("id" to activityId, "publish" to true)),
        )))
        fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()
        try {
            jdbc.sql("update activities set publication_status = 'DRAFT' where id = ?").param(activityId).update()
            // dry run: 계획만(새 조직 아래 프로그램도 계획으로 보인다), 아무것도 쓰지 않는다
            val plan = curationImport.import(file, apply = false)
            assertEquals(listOf("created", "created", "created", "created", "dismiss", "publish"), plan.filter { it.action == "SET" }.map { it.field })
            assertEquals(listOf("reused"), plan.filter { it.action == "SKIP" }.map { it.field })
            assertEquals(0, count("select count(*) from organizations where name like '가져오기%'"))
            assertEquals(1, count("select count(*) from activities where id = $draftId"))
            assertEquals("DRAFT", jdbc.sql("select publication_status from activities where id = ?").param(activityId).query(String::class.java).single())

            // apply: 조직 둘(계위), 프로그램 하나(종류·수집 설정), 같은 이름 두 번째는 건너뛰고, 초안은 지우고(출처·피드백), 게시한다
            val lines = curationImport.import(file, apply = true)
            assertEquals(listOf("같은 조직에 같은 이름의 프로그램이 있음"), lines.filter { it.target == "program dup" }.map { it.detail.substringBefore(":") })
            val programId = jdbc.sql("select p.id from programs p join organizations o on o.id = p.organization_id join organizations parent on parent.id = o.parent_organization_id where p.name = '가져오기 동아리 모집' and o.name_en = 'Import Club' and parent.name = '가져오기 연합동아리'").query(Long::class.java).single()
            assertEquals("CLUB", jdbc.sql("select category from programs where id = ?").param(programId).query(String::class.java).single())
            assertEquals("https://club.example.org/recruit|club.example.org", jdbc.sql("select official_url || '|' || allowed_hosts[1] from program_collection_settings where program_id = ?").param(programId).query(String::class.java).single())
            assertEquals(0, count("select count(*) from activities where id = $draftId"))
            assertEquals(1, count("select count(*) from dismissed_activity_sources where source_id = '$draftSource'"))
            assertEquals(1, count("select count(*) from collection_feedback where kind = 'DRAFT_DISMISSED' and source_text = '가져오기 지울 초안'"))
            assertEquals("PUBLISHED", jdbc.sql("select publication_status from activities where id = ?").param(activityId).query(String::class.java).single())

            // 게시된 활동은 지우지 않는다
            Files.writeString(file, json.writeValueAsString(mapOf("activities" to listOf(mapOf("id" to activityId, "dismiss" to true)))))
            assertEquals(listOf("게시된 활동은 가져오기로 지우지 않습니다"), curationImport.import(file, apply = true).map { it.detail })
            assertEquals(1, count("select count(*) from activities where id = $activityId"))
        } finally {
            jdbc.sql("update activities set publication_status = ? where id = ?").param(status).param(activityId).update()
            jdbc.sql("delete from activities where id = ?").param(draftId).update()
            jdbc.sql("delete from dismissed_activity_sources where source_id = '$draftSource'").update()
            jdbc.sql("delete from collection_feedback where source_text = '가져오기 지울 초안'").update()
            jdbc.sql("delete from program_collection_settings where program_id in (select id from programs where name = '가져오기 동아리 모집')").update()
            jdbc.sql("delete from programs where name = '가져오기 동아리 모집'").update()
            jdbc.sql("delete from organizations where name = '가져오기 동아리'").update()
            jdbc.sql("delete from organizations where name = '가져오기 연합동아리'").update()
        }
    }

    private fun activityName() = jdbc.sql("select name from activities where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").query(String::class.java).single()

    // 명함 회수(native-v1.md "명함 교환"): batch가 매일 04:00에 부르는 RecallService. 규칙 자체는 api 모듈의 CardFlowTest·GuestFlowTest가 확인한다
    @Test
    fun `회수는 30일이 지난 비로그인 저장을 빼고 그 안의 것은 남기며 1개월이 지난 신고와 1년이 지났거나 세션이 없는 비로그인 차단을 지운다`() {
        jdbc.sql("insert into guest_sessions (digest, created_at) values ('old-session-digest', now()), ('new-session-digest', now())").update()
        jdbc.sql("insert into guest_cards (session_digest, card_id, saved_at) values ('old-session-digest', gen_random_uuid(), now() - interval '31 days'), ('new-session-digest', gen_random_uuid(), now())").update()
        // 신고 보유 기간(native-v1.md "명함 신고·차단"): 신고일로부터 1개월. 열린 신고도 지운다
        jdbc.sql(
            """insert into card_reports (id, card_id, reason, card_snapshot, status, created_at) values
               (gen_random_uuid(), gen_random_uuid(), 'SPAM', '{}', 'OPEN', now() - interval '1 month 1 day'),
               (gen_random_uuid(), gen_random_uuid(), 'SPAM', '{}', 'RESOLVED', now() - interval '1 month 1 day'),
               (gen_random_uuid(), gen_random_uuid(), 'OTHER', '{}', 'OPEN', now() - interval '27 days')""",
        ).update()
        // 비로그인 차단 보유 기간(native-v1.md "명함 신고·차단"): 차단일로부터 1년, 세션이 지워졌으면 바로. 로그인 차단은 그대로
        jdbc.sql(
            """insert into card_blocks (id, blocker_user_id, blocker_guest_digest, blocked_user_id, name, created_at) values
               (gen_random_uuid(), null, 'new-session-digest', gen_random_uuid(), 'guest-old', now() - interval '1 year 1 day'),
               (gen_random_uuid(), null, 'new-session-digest', gen_random_uuid(), 'guest-recent', now() - interval '11 months'),
               (gen_random_uuid(), null, 'deleted-session-digest', gen_random_uuid(), 'guest-no-session', now()),
               (gen_random_uuid(), gen_random_uuid(), null, gen_random_uuid(), 'user-old', now() - interval '2 years')""",
        ).update()

        val report = recall.recall()

        assertEquals(RecallService.Report(receipts = 0, guestCards = 1, reports = 2, guestBlocks = 2), report)
        assertEquals(listOf("old-session-digest"), jdbc.sql("select session_digest from guest_cards where recalled_at is not null").query(String::class.java).list())
        assertEquals(listOf("OTHER"), jdbc.sql("select reason from card_reports").query(String::class.java).list())
        assertEquals(listOf("guest-recent", "user-old"), jdbc.sql("select name from card_blocks order by name").query(String::class.java).list())
        jdbc.sql("delete from guest_sessions where digest in ('old-session-digest', 'new-session-digest')").update()
        jdbc.sql("delete from card_reports").update()
        jdbc.sql("delete from card_blocks").update()
    }

    // 맞춤 대기열 잡(native-v1.md "맞춤 대기열"): 로고 대표색 채우기 → 바뀐 활동·태그만 임베딩(날짜·URL·연락처 없는 글) → 새 모델은 다 만든 뒤 넘김 → 사용자 대기열
    @Test
    fun `대기열 잡은 로고 대표색을 채우고 바뀐 활동만 임베딩하며 새 모델은 다 만든 뒤 넘기고 사용자 대기열을 만든다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val png = java.io.ByteArrayOutputStream().also { out ->
            val image = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 8) for (y in 0 until 8) image.setRGB(x, y, if (x < 2) 0xFFFFFFFF.toInt() else 0xFFE5322D.toInt()) // 흰 테두리 + 빨강
            javax.imageio.ImageIO.write(image, "png", out)
        }.toByteArray()
        jdbc.sql("insert into program_logos (program_id, content_type, data, updated_at) values (?, 'image/png', ?, now())").param(programId).param(png).update()
        val open = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, created_at, updated_at)
            values (?, '대기열 배치 활동', '문의 010-1234-5678, https://example.com/apply 2026.11.01 마감, 참가비 30,000원, 만 19세 이상. 6주 동안 팀으로 앱을 만든다', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', 'PUBLISHED', localtimestamp, localtimestamp) returning id
            """
        ).param(programId).query(Long::class.java).single()
        jdbc.sql("insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at) values (?, 'ACTIVITY', 'a', 'EVENT', 'CONFIRMED', '미정', 0, localtimestamp, localtimestamp)").param(open).update()
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', now() - interval '1 day', now() + interval '5 days', 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
            """
        ).param(open).update()
        val user = java.util.UUID.randomUUID()
        jdbc.sql("insert into users (id) values (?)").param(user).update()
        jdbc.sql("insert into recommendation_preferences (user_id, categories, completed_at, updated_at) values (?, '{BOOTCAMP}', now(), now())").param(user).update()
        fun runQueues() {
            val execution = jobOperator.start(buildDiscoveryQueuesJob, JobParametersBuilder().addString("trigger", "test-${System.nanoTime()}").toJobParameters())
            while (execution.isRunning) Thread.sleep(50)
            assertEquals(BatchStatus.COMPLETED, execution.status)
        }
        fun count(sql: String) = jdbc.sql(sql).query(Int::class.java).single()
        val published = count("select count(*) from activities where publication_status = 'PUBLISHED'")
        try {
            Fakes.embeddedTexts.clear()
            runQueues()
            assertEquals("#E5322D", jdbc.sql("select brand_color from program_logos where program_id = ?").param(programId).query(String::class.java).single())
            assertEquals(published, count("select count(*) from activity_embeddings where model = 'fake-model'"))
            assertEquals(count("select count(*) from tags"), count("select count(*) from tag_embeddings where model = 'fake-model'"))
            assertEquals("fake-model", jdbc.sql("select active_model from embedding_settings").query(String::class.java).single())
            // 임베딩 글: 이름·조직·설명은 넣고 전화·URL·날짜·금액·나이 조건은 뺀다
            val text = Fakes.embeddedTexts.single { "대기열 배치 활동" in it }
            assertTrue("프로그램: Let’Swift" in text && "6주 동안 팀으로 앱을 만든다" in text)
            assertTrue(listOf("010-1234-5678", "https://", "2026.11.01", "30,000원", "19세").none { it in text }, text)
            assertEquals(listOf(open), jdbc.sql("select activity_id from discovery_queue where user_id = ?").param(user).query(Long::class.java).list())

            // 다시 돌려도 바뀐 것이 없으면 임베딩하지 않고, 이름을 바꾼 활동만 다시 한다
            Fakes.embeddedTexts.clear()
            runQueues()
            assertEquals(0, Fakes.embeddedTexts.size)
            jdbc.sql("update activities set name = '대기열 배치 활동 2' where id = ?").param(open).update()
            runQueues()
            assertEquals(1, Fakes.embeddedTexts.size)

            // 모델을 바꾸면 새 모델 벡터를 모두 만든 뒤 읽기를 넘기고 옛 모델 벡터를 지운다
            Fakes.embeddingModel = "fake-model-2"
            runQueues()
            assertEquals("fake-model-2", jdbc.sql("select active_model from embedding_settings").query(String::class.java).single())
            assertEquals(0, count("select count(*) from activity_embeddings where model <> 'fake-model-2'") + count("select count(*) from tag_embeddings where model <> 'fake-model-2'"))
            assertEquals(published, count("select count(*) from activity_embeddings"))
        } finally {
            Fakes.embeddingModel = "fake-model"
            jdbc.sql("delete from users where id = ?").param(user).update()
            jdbc.sql("delete from program_logos where program_id = ?").param(programId).update()
            jdbc.sql("delete from activity_embeddings").update()
            jdbc.sql("delete from tag_embeddings").update()
            jdbc.sql("delete from embedding_settings").update()
        }
    }


    // --backfill-hooks-now(2026-10-25): 게시 활동의 빈 대기열 글을 저장된 설명으로 채운다. 페이지를 받지 않고 도구 없이 묻고, 운영자 지정·이미 있는 값·초안은 건드리지 않는다
    @Test
    fun `대기열 글 채우기는 빈 게시 활동만 저장된 설명으로 채우고 운영자 지정과 초안은 그대로 둔다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        fun activity(name: String, status: String, curated: String = "[]") = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, curated_fields, created_at, updated_at)
            values (?, ?, '6주 동안 팀으로 앱을 만드는 과정', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', ?, ?::jsonb, localtimestamp, localtimestamp) returning id
            """
        ).params(programId, name, status, curated).query(Long::class.java).single()
        val empty = activity("글 채우기 빈 활동", "PUBLISHED")
        val curated = activity("글 채우기 운영자 활동", "PUBLISHED", """["hook"]""")
        val draft = activity("글 채우기 초안", "DRAFT")
        fun story(id: Long) = jdbc.sql("select coalesce(hook, '-') || '|' || coalesce(highlights::text, '-') from activities where id = ?").param(id).query(String::class.java).single()

        val report = stories.backfill()

        assertEquals("설명으로 쓴 소개|[{\"icon\": \"team\", \"text\": \"팀 활동\"}]", story(empty))
        assertEquals("-|-", story(curated))
        assertEquals("-|-", story(draft))
        assertTrue(report.filled >= 1 && report.failedBatches == 0)
        // 도구 없이 묻고(--tools "", 도구 서버 없음) 저장된 설명을 넘긴다
        assertEquals("", Fakes.option(Fakes.lastCommand, "--tools"))
        assertFalse("--mcp-config" in Fakes.lastCommand)
        assertTrue("6주 동안 팀으로 앱을 만드는 과정" in Fakes.lastPrompt)
        // 다시 돌리면 채울 것이 없다(채운 활동은 후보에서 빠진다)
        assertTrue(stories.backfill().candidates < report.candidates)
    }

    // 즐겨찾기 알림(native-v1.md "프로그램·활동별 알림 수준"): 기기마다 그 시간대 아침 8시, 신청일만/전체, 활동 수준이 앞섬, 신규는 한 번, 3개 넘으면 묶음, 지운 토큰, 전체 스위치, 인증 정보 없음
    @Test
    fun `즐겨찾기 알림은 기기 시간대 아침 8시에 수준대로 하루 한 번 보내고 3개를 넘으면 묶으며 지운 토큰과 꺼진 스위치를 지킨다`() {
        val seoul = java.time.ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(seoul)
        val now = today.atTime(8, 10).atZone(seoul).toOffsetDateTime()
        val organization = jdbc.sql("insert into organizations (name, created_at, updated_at) values ('알림 확인 조직', localtimestamp, localtimestamp) returning id").query(Long::class.java).single()
        val programId = jdbc.sql("insert into programs (organization_id, name, created_at, updated_at) values (?, '알림 프로그램', localtimestamp, localtimestamp) returning id").param(organization).query(Long::class.java).single()
        fun activity(name: String, publishedAt: java.time.OffsetDateTime? = null): Long = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url, freshness, source_note, publication_status, published_at, created_at, updated_at)
            values (?, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', 'PUBLISHED', ?, localtimestamp, localtimestamp) returning id
            """
        ).param(programId).param(name).param(publishedAt).query(Long::class.java).single()
        fun recruiting(id: Long, start: java.time.OffsetDateTime, end: java.time.OffsetDateTime) = jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone, date_label, sequence, created_at, updated_at)
            values (?, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED', ?, ?, 'Asia/Seoul', '', 0, localtimestamp, localtimestamp)
            """
        ).params(id, start, end).update()
        fun period(id: Long, start: LocalDate?) = jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, time_type, start_date, end_date_exclusive, date_label, sequence, created_at, updated_at)
            values (?, 'ACTIVITY', '행사', 'EVENT', 'CONFIRMED', ?, ?, ?, '미정', 0, localtimestamp, localtimestamp)
            """
        ).param(id).param(start?.let { "ALL_DAY" }).param(start).param(start?.plusDays(2)).update()
        // 새로 게시되어 열림(신규), 오늘 모집 시작, 내일 마감, 오늘 행사 둘
        val fresh = activity("알림 신규", publishedAt = now.minusHours(1)).also { recruiting(it, now.minusMinutes(40), now.plusDays(10)); period(it, null) }
        val starting = activity("알림 모집 시작").also { recruiting(it, now.minusHours(1), now.plusDays(10)); period(it, null) }
        val closing = activity("알림 마감 전날").also { recruiting(it, now.minusDays(3), today.plusDays(1).atTime(18, 0).atZone(seoul).toOffsetDateTime()); period(it, null) }
        val eventA = activity("알림 행사 하나").also { period(it, today) }
        val eventB = activity("알림 행사 둘").also { period(it, today) }
        fun user(level: String, device: String, timeZone: String = "Asia/Seoul"): java.util.UUID = java.util.UUID.randomUUID().also { id ->
            jdbc.sql("insert into users (id) values (?)").param(id).update()
            jdbc.sql("insert into user_favorites (user_id, target_type, target_id, notify_level, created_at) values (?, 'PROGRAM', ?, ?, ?)").params(id, programId, level, now.minusDays(1)).update()
            jdbc.sql("insert into push_devices (token, user_id, platform, time_zone, updated_at) values (?, ?, 'IOS', ?, now())").params(device, id, timeZone).update()
        }
        val applicationOnly = user("APPLICATION", "push-a")
        jdbc.sql("insert into push_devices (token, user_id, platform, time_zone, updated_at) values ('push-gone', ?, 'ANDROID', 'Asia/Seoul', now())").param(applicationOnly).update()
        jdbc.sql("insert into push_devices (token, user_id, platform, time_zone, updated_at) values ('push-ny', ?, 'IOS', 'America/New_York', now())").param(applicationOnly).update()
        val everything = user("ALL", "push-b")
        jdbc.sql("insert into user_favorites (user_id, target_type, target_id, notify_level, created_at) values (?, 'ACTIVITY', ?, 'NONE', now())").params(everything, closing).update()
        val switchedOff = user("ALL", "push-c")
        val noDevice = user("APPLICATION", "push-d")
        jdbc.sql("delete from push_devices where token = 'push-d'").update()
        jdbc.sql("insert into user_notifications (user_id, activity_id, program_id, kind, local_date, created_at) values (?, ?, ?, 'NEW', current_date - 100, now() - interval '100 days')").params(noDevice, eventA, programId).update()
        jdbc.sql("insert into notification_settings (user_id, new_recruitment, updated_at) values (?, false, now())").param(switchedOff).update()
        fun sent(token: String) = Fakes.pushed.filter { it.first == token }.map { it.second }
        val users = listOf(applicationOnly, everything, switchedOff, noDevice)
        try {
            Fakes.pushed.clear()
            pushes.send(now)

            // 신청일만: 신규(모집 시작은 신규에 포함) · 모집 시작 · 내일 마감, 행사는 빠진다. 서울 8시 기기만(뉴욕 기기는 아직)
            val a = sent("push-a")
            assertEquals(listOf("알림 프로그램 새 모집이 열렸어요", "알림 모집 시작 모집이 시작됐어요", "알림 마감 전날 내일 마감").sorted(), a.map { it.title }.sorted())
            val newOne = a.single { it.activityId == fresh }
            assertEquals("https://dearby.wid.io.kr/activities/$fresh", newOne.url)
            // 알림 목록의 그 행을 함께 보낸다(앱이 열 때 읽음)
            assertEquals(jdbc.sql("select id from user_notifications where user_id = ? and activity_id = ? and kind = 'NEW'").params(applicationOnly, fresh).query(Long::class.java).single(), newOne.notificationId)
            assertEquals("알림 신규 · ${now.plusDays(10).monthValue}월 ${now.plusDays(10).dayOfMonth}일 마감", newOne.body)
            assertTrue(sent("push-ny").isEmpty())
            // 지운 기기(410·UNREGISTERED)는 토큰을 지운다
            assertEquals(0, jdbc.sql("select count(*) from push_devices where token = 'push-gone'").query(Int::class.java).single())
            // 전체: 행사 둘까지 4개(마감 전날은 활동 수준 없음이 앞선다) → "오늘 일정 4개" 하나, 즐겨찾기 캘린더로
            val b = sent("push-b").single()
            assertEquals("오늘 일정 4개", b.title)
            assertEquals(null, b.activityId)
            assertEquals("dearby://favorites?view=calendar", b.url)
            assertEquals(4, jdbc.sql("select count(*) from push_deliveries where device_token = 'push-b'").query(Int::class.java).single())
            // 전체 스위치가 꺼지면 보내지 않지만 알림 목록에는 쌓인다. 기기가 없는 사용자도 서울 8시에 쌓이고, 활동 수준 없음은 빠진다
            assertTrue(sent("push-c").isEmpty())
            fun kinds(user: java.util.UUID) = jdbc.sql("select kind from user_notifications where user_id = ? order by kind").param(user).query(String::class.java).list()
            assertEquals(listOf("ACTIVITY_START", "ACTIVITY_START", "APPLICATION_START", "DEADLINE_EVE", "NEW"), kinds(switchedOff))
            assertEquals(listOf("APPLICATION_START", "DEADLINE_EVE", "NEW"), kinds(noDevice))
            assertEquals(listOf("ACTIVITY_START", "ACTIVITY_START", "APPLICATION_START", "NEW"), kinds(everything))
            // 90일 지난 알림은 지운다
            assertEquals(0, jdbc.sql("select count(*) from user_notifications where created_at < now() - interval '90 days'").query(Int::class.java).single())
            assertTrue(starting > 0 && eventA > 0 && eventB > 0)

            // 같은 날 다시 돌려도 보내지 않고, 다음 날에는 신규를 다시 보내지 않는다(마감 당일만)
            Fakes.pushed.clear()
            pushes.send(now.plusMinutes(30))
            assertTrue(Fakes.pushed.isEmpty())
            // 뉴욕 기기는 그 시간대 8시에 그 날짜의 알림 행을 따로 받는다(기기마다 한 번)
            val nyNow = today.atTime(8, 10).atZone(java.time.ZoneId.of("America/New_York")).toOffsetDateTime()
            pushes.send(nyNow)
            assertEquals(listOf("알림 마감 전날 내일 마감", "알림 모집 시작 모집이 시작됐어요", "알림 프로그램 새 모집이 열렸어요"), sent("push-ny").map { it.title }.sorted())
            assertTrue(sent("push-a").isEmpty())
            pushes.send(nyNow.plusMinutes(20))
            assertEquals(3, sent("push-ny").size)
            Fakes.pushed.clear()
            pushes.send(now.plusDays(1))
            assertEquals(listOf("알림 마감 전날 오늘 마감"), sent("push-a").map { it.title })

            // 인증 정보가 없으면 발송만 건너뛴다(기기 시각과 상관없는 수동 실행도)
            Fakes.pushed.clear()
            Fakes.pushProblem = "APNS_KEY_P8 없음"
            jdbc.sql("delete from push_deliveries").update()
            assertEquals(0, pushes.send(now, anyHour = true).notifications)
            assertTrue(Fakes.pushed.isEmpty())
        } finally {
            Fakes.pushProblem = null
            users.forEach { jdbc.sql("delete from users where id = ?").param(it).update() }
            jdbc.sql("delete from activities where program_id = ?").param(programId).update()
            jdbc.sql("delete from programs where id = ?").param(programId).update()
            jdbc.sql("delete from organizations where id = ?").param(organization).update()
        }
    }

}
