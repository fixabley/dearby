package io.wid.dearby.batch.application

import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.CollectionOutcome
import io.wid.dearby.domain.CollectionReport
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramSource
import org.springframework.stereotype.Service
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 프로그램 수집 설정 하나를 수집한다: 후보 페이지 찾기 → 직접 받기 → 활동 추출 → 원문 대조 → 초안 저장
@Service
class CollectActivitiesService(
    private val programs: ProgramRepository,
    private val discoverer: ActivityDiscoverer,
    private val fetcher: PageFetcher,
    private val extractor: ActivityExtractor,
    private val drafts: ActivityDraftRepository,
) {

    fun collect(source: ProgramSource): CollectionReport {
        if (source.allowedHosts.isEmpty()) return CollectionReport(CollectionOutcome.SKIPPED, error = "허용 호스트가 없음")
        val program = programs.findAllById(listOf(source.programId)).singleOrNull()
            ?: return CollectionReport(CollectionOutcome.SKIPPED, error = "프로그램 ${source.programId}이 없음")
        val discovery = discoverer.discover(source, program.name)
        var usage = discovery.usage
        val rejected = mutableListOf<String>()
        var fetched = 0
        var found = 0
        var saved = 0
        for (url in (listOf(source.officialUrl) + discovery.urls).distinct().take(MAX_PAGES)) {
            val page = try {
                fetcher.fetch(url, source.allowedHosts)
            } catch (e: PageFetchException) {
                rejected += "$url: ${e.message}"
                continue
            }
            fetched++
            val extraction = extractor.extract(page, program.name)
            usage += extraction.usage
            found += extraction.activities.size
            val checkedAt = OffsetDateTime.now(ZoneOffset.UTC)
            for (candidate in extraction.activities) {
                when (val result = ActivityVerifier.verify(candidate, page, checkedAt)) {
                    is Verification.Rejected -> rejected += "${candidate.name}: ${result.reason}"
                    is Verification.Accepted ->
                        if (drafts.saveDraft(source.programId, sourceId(source.programId, page.url, candidate.roundKey), result.activity)) saved++
                        else rejected += "${candidate.name}: 이미 게시된 활동이라 덮어쓰지 않음"
                }
            }
        }
        return CollectionReport(
            // 페이지를 하나도 받지 못하면 실패. 후보가 없거나 모두 제외된 것은 결과로 남긴다
            outcome = if (fetched == 0) CollectionOutcome.FAILED else CollectionOutcome.SUCCEEDED,
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

        // 프로그램 + 정규화한 페이지 주소 + 회차 식별자. 다시 수집해도 같은 활동이면 같은 값
        fun sourceId(programId: Id, url: String, roundKey: String): UUID {
            val uri = URI(url)
            val normalized = "${uri.scheme}://${uri.host.lowercase()}${uri.path.trimEnd('/')}"
            return UUID.nameUUIDFromBytes("dearby/activity/$programId|$normalized|${roundKey.trim()}".toByteArray())
        }
    }
}
