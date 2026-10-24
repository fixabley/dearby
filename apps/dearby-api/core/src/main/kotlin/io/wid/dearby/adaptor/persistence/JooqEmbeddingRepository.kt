package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_EMBEDDINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAG_EMBEDDINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_TASTE
import io.wid.dearby.application.discovery.EmbeddingRepository
import io.wid.dearby.domain.Id
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JooqEmbeddingRepository(private val dsl: DSLContext) : EmbeddingRepository {

    override fun activityHashes(model: String): Map<Id, String> =
        dsl.select(ACTIVITY_EMBEDDINGS.ACTIVITY_ID, ACTIVITY_EMBEDDINGS.SOURCE_HASH).from(ACTIVITY_EMBEDDINGS)
            .where(ACTIVITY_EMBEDDINGS.MODEL.eq(model)).fetch { it.value1()!! to it.value2()!! }.toMap()

    override fun saveActivity(activityId: Id, model: String, vector: FloatArray, hash: String) {
        dsl.execute(
            """
            insert into activity_embeddings (activity_id, model, embedding, source_hash, updated_at) values (?, ?, cast(? as halfvec), ?, now())
            on conflict (activity_id, model) do update set embedding = excluded.embedding, source_hash = excluded.source_hash, updated_at = excluded.updated_at
            """.trimIndent(),
            activityId, model, VectorRows.text(vector), hash,
        )
    }

    override fun tagHashes(model: String): Map<Id, String> =
        dsl.select(TAG_EMBEDDINGS.TAG_ID, TAG_EMBEDDINGS.SOURCE_HASH).from(TAG_EMBEDDINGS)
            .where(TAG_EMBEDDINGS.MODEL.eq(model)).fetch { it.value1()!! to it.value2()!! }.toMap()

    override fun saveTag(tagId: Id, model: String, vector: FloatArray, hash: String) {
        dsl.execute(
            """
            insert into tag_embeddings (tag_id, model, embedding, source_hash, updated_at) values (?, ?, cast(? as halfvec), ?, now())
            on conflict (tag_id, model) do update set embedding = excluded.embedding, source_hash = excluded.source_hash, updated_at = excluded.updated_at
            """.trimIndent(),
            tagId, model, VectorRows.text(vector), hash,
        )
    }

    @Transactional
    override fun switchModel(model: String) {
        dsl.execute(
            "insert into embedding_settings (id, active_model, updated_at) values (true, ?, now()) on conflict (id) do update set active_model = excluded.active_model, updated_at = excluded.updated_at",
            model,
        )
        dsl.deleteFrom(ACTIVITY_EMBEDDINGS).where(ACTIVITY_EMBEDDINGS.MODEL.ne(model)).execute()
        dsl.deleteFrom(TAG_EMBEDDINGS).where(TAG_EMBEDDINGS.MODEL.ne(model)).execute()
        dsl.deleteFrom(USER_TASTE).where(USER_TASTE.MODEL.ne(model)).execute()
    }
}
