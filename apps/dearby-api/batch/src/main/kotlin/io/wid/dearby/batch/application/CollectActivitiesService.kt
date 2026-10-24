package io.wid.dearby.batch.application

import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.collection.CollectionFeedbackRepository
import io.wid.dearby.application.collection.WatchlistRepository
import io.wid.dearby.domain.collection.CollectionFeedback
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionOutcome
import io.wid.dearby.domain.collection.CollectionReport
import io.wid.dearby.domain.collection.CollectionTarget
import io.wid.dearby.domain.collection.CollectionUrlTemplate
import io.wid.dearby.domain.collection.DraftSaveResult
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.util.*

// 프로그램 하나를 수집한다: 에이전트가 페이지를 받아 보고 활동 후보를 제출하면, 제출마다 원문 대조 → 조심 목록 → 운영자 피드백 → 초안 저장을 하고 판정을 돌려준다.
// 허용 호스트가 있으면 공식 도메인 안에서만, 없으면 웹 전체에서 찾고 그 초안은 공식 출처가 아님(official_source = false)으로 남긴다
@Service
class CollectActivitiesService(
    private val agent: ActivityAgent,
    private val fetcher: PageFetcher,
    private val renderer: RenderingPageFetcher, // renderPages가 켜진 프로그램의 페이지와 render_page 도구는 브라우저 렌더링으로 받는다(admin-v1.md)
    private val drafts: ActivityDraftRepository,
    private val watchlist: WatchlistRepository,
    private val tags: io.wid.dearby.application.tag.TagRepository,
    private val feedback: CollectionFeedbackRepository,
    private val places: PlaceResolver, // 장소 지오코딩(native-v1.md "수집기 장소"). 확실할 때만 좌표
    private val json: JsonMapper,
) {

    fun collect(target: CollectionTarget): CollectionReport {
        // 조심 목록(admin-v1.md): 운영자가 지정한 제외 이름·다른 표기. 지시문 힌트로 넘기고, 저장 전 이름 대조로 보장한다
        val watched = watchlist.findAllByProgramId(target.programId)
        // 이전 기수(admin-v1.md "이전 기수 정보 자동완성"): 지시문 힌트. 비어 있는 필드는 저장할 때 서버가 채운다. 기수 번호는 시작 주소의 {round}에도 쓴다
        val previous = drafts.findPreviousEdition(target.programId)
        // 운영자 피드백(collection_feedback): 이 프로그램의 최근 기록을 요청에 넣는다. 더 필요하면 에이전트가 search_feedback으로 찾는다
        val recent = feedback.latest(target.programId, RECENT_FEEDBACK)
        // 태그 사전(native-v1.md "태그", "분야·성격 태그와 아이콘"): 모델이 이 이름 중에서만 고르게 한다
        val dictionary = tags.findAll()
        val tools = Tools(target, watched)
        tools.injected += recent.map { it.id }
        // 시작 주소 토큰(admin-v1.md "주소 토큰"): 올해·내년, 다음·현재 기수로 펼친다. 기수를 모르면 그 주소는 건너뛰고 이유만 남긴다
        val template = target.officialUrlTemplate
        val startUrls = template?.let { CollectionUrlTemplate.expand(it, LocalDate.now(SEOUL), CollectionUrlTemplate.previousRound(previous?.name)) }.orEmpty()
        if (template != null && startUrls.isEmpty()) tools.rejected += "$template: 기수 번호를 알 수 없음"
        // 피드백 검색의 "같은 호스트": 허용 호스트, 없으면 시작 주소의 호스트. 둘 다 없으면(웹 전체 수집) 같은 프로그램 다음은 유사도 순
        tools.hosts = target.allowedHosts.ifEmpty { startUrls.mapNotNull { runCatching { URI(it).host }.getOrNull() } }
        val run = try {
            agent.run(AgentInput(target.copy(officialUrls = startUrls), watched, previous, recent, dictionary), tools)
        } finally {
            // 렌더링을 쓴 프로그램이 끝나면 브라우저를 내린다(다음 프로그램이 쓰면 다시 띄운다)
            if (tools.rendered) renderer.close()
        }
        // 한도로 멈췄으면 운영자가 보도록 거절 이유에도 남긴다
        run.log.takeIf { it.capReached }?.let { tools.rejected += "수집 중단: ${it.stopReason}" }
        val log = run.log.copy(feedbackInjected = tools.injected, feedbackRetrieved = tools.retrieved.distinct(), feedbackApplied = tools.applied.distinct())
        return CollectionReport(
            // 받을 페이지가 없었으면 건너뜀(찾은 페이지 없음), 받으려 했지만 모두 실패하면 실패. 후보가 없거나 모두 제외된 것은 결과로 남긴다
            outcome = when {
                tools.pages.isNotEmpty() -> CollectionOutcome.SUCCEEDED
                tools.rejected.isEmpty() -> CollectionOutcome.SKIPPED
                else -> CollectionOutcome.FAILED
            },
            foundCount = tools.found,
            savedCount = tools.saved.size,
            rejectedReasons = tools.rejected,
            inputTokens = run.usage.totalInputTokens,
            outputTokens = run.usage.outputTokens,
            webSearchRequests = run.usage.webSearchRequests,
            fetchedUrls = tools.pages.keys.toList(),
            agentLog = json.writeValueAsString(log),
        )
    }

    // 프로그램 하나의 수집 동안 받은 페이지·제출 결과를 모은다
    private inner class Tools(private val target: CollectionTarget, private val watched: List<WatchEntry>) : AgentTools {
        val pages = LinkedHashMap<String, FetchedPage>() // 요청한 주소 → 받은 페이지(받기 순서, 실패 제외)
        val rejected = mutableListOf<String>()
        val saved = mutableSetOf<UUID>() // 같은 후보를 고쳐 다시 내도 한 번으로 센다
        var found = 0
        var rendered = false
        var hosts: List<String> = emptyList() // 피드백 검색에서 "같은 호스트"로 볼 호스트(허용 호스트, 없으면 시작 주소의 호스트)
        val injected = mutableListOf<Id>()
        val retrieved = mutableListOf<Id>()
        val applied = mutableListOf<Id>()

        override fun fetch(url: String) = load(url, render = target.renderPages)

        override fun render(url: String) = load(url, render = true)

        private fun load(url: String, render: Boolean): FetchedPage = try {
            if (render) rendered = true
            (if (render) renderer.fetch(url, target.allowedHosts) else fetcher.fetch(url, target.allowedHosts)).also { pages[url] = it }
        } catch (e: PageFetchException) {
            rejected += "$url: ${e.message}"
            throw e
        }

        override fun submit(candidate: ExtractedActivity, sourceUrl: String): SubmitVerdict {
            found++
            val page = pages[sourceUrl] ?: return reject(candidate, "이번 수집에서 받은 페이지가 아님: $sourceUrl")
            val result = ActivityVerifier.verify(candidate, page, target.officialOnly, OffsetDateTime.now(ZoneOffset.UTC))
            if (result is Verification.Rejected) return reject(candidate, result.reason)
            val activity = (result as Verification.Accepted).activity
            val entry = watched.firstOrNull { it.normalizedName == WatchEntry.normalize(candidate.name) }
            val (sourceId, draft) = when (entry?.kind) {
                WatchKind.EXCLUDE -> return reject(candidate, "조심 목록: 제외 ${entry.name}")
                // 다른 표기: 정본 활동의 초안을 정본 이름으로 갱신한다. 정본이 지워졌거나 수집 출처가 없으면 저장할 곳이 없다
                WatchKind.ALIAS -> (entry.canonicalSourceId ?: return reject(candidate, "조심 목록: 정본 활동 없음(${entry.name})")) to
                    activity.copy(name = entry.canonicalActivityName ?: activity.name)
                null -> sourceId(target.programId, candidate.roundKey) to activity
            }
            // 운영자가 지운 초안·제외한 이름과 같은 이름이면 저장하지 않는다(이 프로그램 안에서만)
            feedback.findByName(target.programId, candidate.name, REJECTING).firstOrNull()?.let { row ->
                applied += row.id
                return reject(candidate, "운영자 피드백 #${row.id}(${row.kind}): \"${row.sourceText}\"${row.reason?.let { "(이유: $it)" } ?: ""}", retry = false)
            }
            val warnings = dateWarnings(candidate)
            // 저장 직전에만 지오코딩한다(조심 목록·피드백으로 거절될 후보는 검색하지 않는다)
            val placed = ActivityVerifier.place(candidate, page)?.let(places::resolve)?.let { draft.copy(place = it) } ?: draft
            return when (drafts.saveDraft(target.programId, sourceId, placed)) {
                DraftSaveResult.SAVED -> { saved += sourceId; SubmitVerdict(true, "초안으로 저장됨", warnings) }
                DraftSaveResult.SKIPPED_PUBLISHED -> reject(candidate, "이미 게시된 활동이라 덮어쓰지 않음", retry = false)
                DraftSaveResult.SKIPPED_DISMISSED -> reject(candidate, "관리자가 삭제한 활동이라 다시 만들지 않음", retry = false)
            }
        }

        override fun searchFeedback(text: String, kind: FeedbackKind?): List<CollectionFeedback> =
            feedback.search(target.programId, hosts, text, kind, SEARCH_LIMIT).also { rows -> retrieved += rows.map { it.id } }

        // 활동 기간·세부 일정의 날짜 문구가 운영자가 날짜를 지정한 문구와 많이 비슷한데 해석이 다르면 주의를 돌려준다(연도가 바뀌면 정당하게 다를 수 있어 거절하지 않는다)
        private fun dateWarnings(candidate: ExtractedActivity): List<String> {
            val labels = listOf(Interpretation(candidate.dateLabel, candidate.startDate, candidate.endDate, null, null)) +
                candidate.schedules.map { Interpretation(it.dateLabel, it.startDate, it.endDate, it.startAt, it.endAt) }
            return labels.filter { it.label.isNotBlank() }.mapNotNull { mine ->
                val row = feedback.search(target.programId, hosts, mine.label, FeedbackKind.DATE_CURATED, SEARCH_LIMIT)
                    .firstOrNull { (it.similarity ?: 0.0) >= WARN_SIMILARITY } ?: return@mapNotNull null
                val curated = json.readTree(row.correctedValue)
                val theirs = Interpretation(row.sourceText, curated.text("startDate"), curated.text("endDate"), curated.text("startAt"), curated.text("endAt"))
                if (mine.sameAs(theirs)) return@mapNotNull null
                applied += row.id
                "주의(운영자 피드백 #${row.id}): \"${row.sourceText}\"를 운영자는 ${theirs.describe()}로 지정했다${row.reason?.let { "(이유: $it)" } ?: ""}. 제출한 \"${mine.label}\" 해석은 ${mine.describe()}"
            }
        }

        // retry = false: 고쳐도 저장되지 않는 거절이라 모델이 다시 내지 않게 알린다
        private fun reject(candidate: ExtractedActivity, reason: String, retry: Boolean = true): SubmitVerdict {
            rejected += "${candidate.name}: $reason"
            return SubmitVerdict(false, if (retry) reason else "$reason(다시 제출하지 않는다)")
        }
    }

    // 날짜 문구 하나의 해석. 시각은 같은 순간이면 같다고 본다
    private data class Interpretation(val label: String, val startDate: String?, val endDate: String?, val startAt: String?, val endAt: String?) {
        fun sameAs(other: Interpretation) = startDate == other.startDate && endDate == other.endDate &&
            instant(startAt) == instant(other.startAt) && instant(endAt) == instant(other.endAt)

        fun describe() = listOfNotNull(startDate?.let { "startDate $it" }, endDate?.let { "endDate $it" }, startAt?.let { "startAt $it" }, endAt?.let { "endAt $it" })
            .ifEmpty { listOf("날짜 없음") }.joinToString(", ")

        private fun instant(value: String?) = value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val RECENT_FEEDBACK = 10
        private const val SEARCH_LIMIT = 8
        // 날짜 문구 주의 기준(글자 세 개 단위 유사도). 검색 기준(0.3)보다 높여 거의 같은 문구에만 주의를 붙인다
        private const val WARN_SIMILARITY = 0.6
        private val REJECTING = listOf(FeedbackKind.DRAFT_DISMISSED, FeedbackKind.WATCH_EXCLUDE)

        private fun JsonNode.text(key: String): String? = path(key).takeIf { it.isString }?.asString()

        // 프로그램 + 회차 식별자(공백·대소문자 정규화). 같은 모집을 다른 페이지나 다른 날에 다시 만나도 같은 초안으로 모인다(2026-10-18, 페이지 주소를 뺐다)
        fun sourceId(programId: Id, roundKey: String): UUID =
            UUID.nameUUIDFromBytes("dearby/activity/$programId|${roundKey.trim().lowercase().replace(Regex("\\s+"), " ")}".toByteArray())
    }
}
