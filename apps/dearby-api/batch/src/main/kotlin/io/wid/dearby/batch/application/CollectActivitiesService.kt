package io.wid.dearby.batch.application

import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionOutcome
import io.wid.dearby.domain.collection.CollectionReport
import io.wid.dearby.domain.collection.CollectionTarget
import io.wid.dearby.domain.collection.DraftSaveResult
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
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
) {

    fun collect(target: CollectionTarget): CollectionReport {
        val discovery = discoverer.discover(target)
        var usage = discovery.usage
        val rejected = mutableListOf<String>()
        var fetched = 0
        var found = 0
        var saved = 0
        for (url in (listOfNotNull(target.officialUrl) + discovery.urls).distinct().take(MAX_PAGES)) {
            val page = try {
                fetcher.fetch(url, target.allowedHosts)
            } catch (e: PageFetchException) {
                rejected += "$url: ${e.message}"
                continue
            }
            fetched++
            val extraction = extractor.extract(page, target.programName)
            usage += extraction.usage
            found += extraction.activities.size
            val checkedAt = OffsetDateTime.now(ZoneOffset.UTC)
            for (candidate in extraction.activities) {
                when (val result = ActivityVerifier.verify(candidate, page, target.officialOnly, checkedAt)) {
                    is Verification.Rejected -> rejected += "${candidate.name}: ${result.reason}"
                    is Verification.Accepted ->
                        when (drafts.saveDraft(target.programId, sourceId(target.programId, candidate.roundKey), result.activity)) {
                            DraftSaveResult.SAVED -> saved++
                            DraftSaveResult.SKIPPED_PUBLISHED -> rejected += "${candidate.name}: 이미 게시된 활동이라 덮어쓰지 않음"
                            DraftSaveResult.SKIPPED_DISMISSED -> rejected += "${candidate.name}: 관리자가 삭제한 활동이라 다시 만들지 않음"
                        }
                }
            }
        }
        return CollectionReport(
            // 받을 페이지가 없었으면 건너뜀(찾은 페이지 없음), 받으려 했지만 모두 실패하면 실패. 후보가 없거나 모두 제외된 것은 결과로 남긴다
            outcome = when {
                fetched > 0 -> CollectionOutcome.SUCCEEDED
                rejected.isEmpty() -> CollectionOutcome.SKIPPED
                else -> CollectionOutcome.FAILED
            },
            foundCount = found,
            savedCount = saved,
            rejectedReasons = rejected,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            webSearchRequests = usage.webSearchRequests,
        )
    }

    companion object {
        const val MAX_PAGES = 5

        // 프로그램 + 회차 식별자(공백·대소문자 정규화). 같은 모집을 다른 페이지나 다른 날에 다시 만나도 같은 초안으로 모인다(2026-10-18, 페이지 주소를 뺐다)
        fun sourceId(programId: Id, roundKey: String): UUID =
            UUID.nameUUIDFromBytes("dearby/activity/$programId|${roundKey.trim().lowercase().replace(Regex("\\s+"), " ")}".toByteArray())
    }
}
