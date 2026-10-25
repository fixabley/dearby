package io.wid.dearby.batch.application

import io.wid.dearby.application.collection.ActivityDraftRepository
import io.wid.dearby.application.collection.StorylessActivity
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.StoryTexts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// 게시 활동의 비어 있는 대기열 글(native-v1.md "맞춤 대기열" hook·highlights)을 저장된 설명으로 채운다(--backfill-hooks-now, 2026-10-25).
// 페이지를 받지 않는다. 운영자 지정·이미 있는 값은 건드리지 않는다(ActivityDraftRepository.fillStory). 묶음 하나가 실패해도 다음 묶음을 계속한다
@Service
class StoryBackfillService(private val drafts: ActivityDraftRepository, private val writer: StoryWriter) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class Report(val candidates: Int, val filled: Int, val failedBatches: Int)

    fun backfill(): Report {
        val candidates = drafts.findStoryless()
        var filled = 0
        var failed = 0
        candidates.chunked(BATCH).forEach { batch ->
            runCatching { writer.write(batch) }
                .onFailure { failed++; log.warn("Story backfill batch failed ({} activities)", batch.size, it) }
                .getOrNull()?.forEach { (id, story) ->
                    val hook = StoryTexts.collectedHook(story.hook)
                    val highlights = StoryTexts.collectedHighlights(story.highlights)
                    if (id in batch.map { it.id } && drafts.fillStory(id, hook, highlights)) filled++
                }
        }
        return Report(candidates.size, filled, failed)
    }

    private companion object {
        const val BATCH = 10
    }
}

// 활동 설명으로 대기열 글을 쓴다(모델). 결과는 활동 id → 글. 검증은 부르는 쪽이 한다
fun interface StoryWriter {
    fun write(activities: List<StorylessActivity>): Map<Id, WrittenStory>
}

data class WrittenStory(val hook: String?, val highlights: List<Pair<String?, String?>>)
