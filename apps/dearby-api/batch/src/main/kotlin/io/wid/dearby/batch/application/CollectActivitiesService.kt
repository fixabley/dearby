package io.wid.dearby.batch.application

import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.collection.WatchlistRepository
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
import java.util.*

// 프로그램 하나를 수집한다: 후보 페이지 찾기 → 직접 받기 → 활동 추출 → 원문 대조 → 초안 저장.
// 허용 호스트가 있으면 공식 도메인 안에서만, 없으면 웹 전체에서 찾고 그 초안은 공식 출처가 아님(official_source = false)으로 남긴다
@Service
class CollectActivitiesService(
    private val discoverer: ActivityDiscoverer,
    private val fetcher: PageFetcher,
    private val extractor: ActivityExtractor,
    private val drafts: ActivityDraftRepository,
    private val watchlist: WatchlistRepository,
) {

    fun collect(target: CollectionTarget): CollectionReport {
        // 조심 목록(admin-v1.md): 운영자가 지정한 제외 이름·다른 표기. 지시문 힌트로 넘기고, 저장 전 이름 대조로 보장한다
        val watched = watchlist.findAllByProgramId(target.programId)
        // 이전 기수(admin-v1.md "이전 기수 정보 자동완성"): 지시문 힌트. 비어 있는 필드는 저장할 때 서버가 채운다. 기수 번호는 시작 주소의 {round}에도 쓴다
        val previous = drafts.findPreviousEdition(target.programId)
        val rejected = mutableListOf<String>()
        // 시작 주소 토큰(admin-v1.md "주소 토큰"): 올해·내년, 다음·현재 기수로 펼친다. 기수를 모르면 그 주소는 건너뛰고 이유만 남긴다
        val template = target.officialUrlTemplate
        val startUrls = template?.let { CollectionUrlTemplate.expand(it, LocalDate.now(SEOUL), CollectionUrlTemplate.previousRound(previous?.name)) }.orEmpty()
        if (template != null && startUrls.isEmpty()) rejected += "$template: 기수 번호를 알 수 없음"
        val resolved = target.copy(officialUrls = startUrls)
        val discovery = discoverer.discover(resolved)
        var usage = discovery.usage
        val fetched = mutableListOf<String>()
        var found = 0
        var saved = 0
        for (url in (startUrls + discovery.urls).distinct().take(MAX_PAGES)) {
            val page = try {
                fetcher.fetch(url, target.allowedHosts)
            } catch (e: PageFetchException) {
                rejected += "$url: ${e.message}"
                continue
            }
            fetched += url
            val extraction = extractor.extract(page, target.programName, watched, previous)
            usage += extraction.usage
            found += extraction.activities.size
            val checkedAt = OffsetDateTime.now(ZoneOffset.UTC)
            for (candidate in extraction.activities) {
                when (val result = ActivityVerifier.verify(candidate, page, target.officialOnly, checkedAt)) {
                    is Verification.Rejected -> rejected += "${candidate.name}: ${result.reason}"
                    is Verification.Accepted -> {
                        val entry = watched.firstOrNull { it.normalizedName == WatchEntry.normalize(candidate.name) }
                        val (sourceId, activity) = when (entry?.kind) {
                            WatchKind.EXCLUDE -> { rejected += "${candidate.name}: 조심 목록: 제외 ${entry.name}"; continue }
                            // 다른 표기: 정본 활동의 초안을 정본 이름으로 갱신한다. 정본이 지워졌거나 수집 출처가 없으면 저장할 곳이 없다
                            WatchKind.ALIAS -> {
                                val canonicalSource = entry.canonicalSourceId
                                if (canonicalSource == null) { rejected += "${candidate.name}: 조심 목록: 정본 활동 없음(${entry.name})"; continue }
                                canonicalSource to result.activity.copy(name = entry.canonicalActivityName ?: result.activity.name)
                            }
                            null -> sourceId(target.programId, candidate.roundKey) to result.activity
                        }
                        when (drafts.saveDraft(target.programId, sourceId, activity)) {
                            DraftSaveResult.SAVED -> saved++
                            DraftSaveResult.SKIPPED_PUBLISHED -> rejected += "${candidate.name}: 이미 게시된 활동이라 덮어쓰지 않음"
                            DraftSaveResult.SKIPPED_DISMISSED -> rejected += "${candidate.name}: 관리자가 삭제한 활동이라 다시 만들지 않음"
                        }
                    }
                }
            }
        }
        return CollectionReport(
            // 받을 페이지가 없었으면 건너뜀(찾은 페이지 없음), 받으려 했지만 모두 실패하면 실패. 후보가 없거나 모두 제외된 것은 결과로 남긴다
            outcome = when {
                fetched.isNotEmpty() -> CollectionOutcome.SUCCEEDED
                rejected.isEmpty() -> CollectionOutcome.SKIPPED
                else -> CollectionOutcome.FAILED
            },
            foundCount = found,
            savedCount = saved,
            rejectedReasons = rejected,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            webSearchRequests = usage.webSearchRequests,
            fetchedUrls = fetched,
        )
    }

    companion object {
        const val MAX_PAGES = 5
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        // 프로그램 + 회차 식별자(공백·대소문자 정규화). 같은 모집을 다른 페이지나 다른 날에 다시 만나도 같은 초안으로 모인다(2026-10-18, 페이지 주소를 뺐다)
        fun sourceId(programId: Id, roundKey: String): UUID =
            UUID.nameUUIDFromBytes("dearby/activity/$programId|${roundKey.trim().lowercase().replace(Regex("\\s+"), " ")}".toByteArray())
    }
}
