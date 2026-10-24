package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivitySearch
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.tag.TagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// 게시 활동과 태그 사전을 임베딩한다(밤 배치). 글이 바뀐 것만 다시 하고, 새 모델이면 모두 만든 뒤에만 읽는 모델을 넘긴다
@Service
class EmbeddingService(
    private val activities: ActivityService,
    private val tags: TagRepository,
    private val embeddings: EmbeddingRepository,
    private val discovery: DiscoveryRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class Report(val activities: Int, val embedded: Int, val tags: Int, val failed: Int, val switched: Boolean)

    fun embedAll(embedder: TextEmbedder): Report {
        val model = embedder.modelId
        var failed = 0
        fun <T> embedChanged(items: List<T>, stored: Map<Long, String>, id: (T) -> Long, text: (T) -> String, save: (Long, FloatArray, String) -> Unit): Int =
            items.count { item ->
                val source = text(item)
                val hash = EmbeddingText.hash(model, source)
                if (stored[id(item)] == hash) return@count false
                runCatching { save(id(item), embedder.embed(source), hash) }
                    .onFailure { failed++; log.warn("Embedding failed for {}", id(item), it) }.isSuccess
            }
        val published = publishedListings()
        val embedded = embedChanged(published, embeddings.activityHashes(model), { it.activity.id }, EmbeddingText::of) { id, v, h -> embeddings.saveActivity(id, model, v, h) }
        val tagCount = embedChanged(tags.findAll(), embeddings.tagHashes(model), { it.id }, EmbeddingText::of) { id, v, h -> embeddings.saveTag(id, model, v, h) }
        // 모델을 바꿀 때는 이번 모델 벡터가 모두 생긴 뒤에만 읽기를 넘긴다(하나라도 실패하면 다음 밤에 다시)
        val switched = discovery.activeModel() != model && failed == 0
        if (switched) embeddings.switchModel(model)
        return Report(published.size, embedded, tagCount, failed, switched)
    }

    private fun publishedListings(): List<ActivityListing> = buildList {
        var cursor: Long? = null
        do {
            val page = activities.search(ActivitySearch(null, null, null, cursor, ActivityService.MAX_PAGE_SIZE))
            addAll(page.items)
            cursor = page.nextCursor
        } while (cursor != null)
    }
}
