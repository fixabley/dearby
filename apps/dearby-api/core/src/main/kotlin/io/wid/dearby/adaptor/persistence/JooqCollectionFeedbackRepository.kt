package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_FEEDBACK
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.application.collection.CollectionFeedbackRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectedActivityRef
import io.wid.dearby.domain.collection.CollectionFeedback
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.collection.NewFeedback
import io.wid.dearby.domain.collection.WatchEntry
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqCollectionFeedbackRepository(private val dsl: DSLContext) : CollectionFeedbackRepository {

    override fun record(feedback: NewFeedback): Id =
        dsl.insertInto(COLLECTION_FEEDBACK)
            .set(COLLECTION_FEEDBACK.KIND, feedback.kind.name)
            .set(COLLECTION_FEEDBACK.PROGRAM_ID, feedback.programId)
            .set(COLLECTION_FEEDBACK.HOST, feedback.host)
            .set(COLLECTION_FEEDBACK.SOURCE_TEXT, feedback.sourceText.take(MAX_SOURCE))
            .set(COLLECTION_FEEDBACK.CORRECTED_VALUE, JSONB.valueOf(JSON.writeValueAsString(feedback.correctedValue)))
            .set(COLLECTION_FEEDBACK.REASON, feedback.reason?.take(MAX_REASON))
            .set(COLLECTION_FEEDBACK.ACTIVITY_ID, feedback.activityId)
            .set(COLLECTION_FEEDBACK.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .returning(COLLECTION_FEEDBACK.ID)
            .fetchOne()!!
            .id!!

    override fun collectedActivity(activityId: Id): CollectedActivityRef? =
        dsl.select(ACTIVITIES.PROGRAM_ID, ACTIVITIES.NAME, ACTIVITIES.OFFICIAL_URL)
            .from(ACTIVITIES)
            .where(ACTIVITIES.ID.eq(activityId), ACTIVITIES.SOURCE_ID.isNotNull)
            .fetchOne { CollectedActivityRef(activityId, it[ACTIVITIES.PROGRAM_ID]!!, it[ACTIVITIES.NAME]!!, host(it[ACTIVITIES.OFFICIAL_URL])) }

    override fun latest(programId: Id, limit: Int): List<CollectionFeedback> =
        select(null)
            .where(COLLECTION_FEEDBACK.PROGRAM_ID.eq(programId))
            .orderBy(COLLECTION_FEEDBACK.CREATED_AT.desc(), COLLECTION_FEEDBACK.ID.desc())
            .limit(limit)
            .fetch { feedback(it, null) }

    override fun search(programId: Id, hosts: Collection<String>, text: String, kind: FeedbackKind?, limit: Int): List<CollectionFeedback> {
        val similarity = DSL.field("similarity({0}, {1})", Double::class.java, COLLECTION_FEEDBACK.SOURCE_TEXT, DSL.`val`(text))
        val rank = DSL.`when`(COLLECTION_FEEDBACK.PROGRAM_ID.eq(programId), 0)
            .`when`(COLLECTION_FEEDBACK.HOST.`in`(hosts.map { it.lowercase() }), 1)
            .otherwise(2)
        return select(similarity)
            // %는 GIN 인덱스를 쓰는 유사도 조건(pg_trgm.similarity_threshold, 기본 0.3)
            .where(DSL.condition("{0} % {1}", COLLECTION_FEEDBACK.SOURCE_TEXT, DSL.`val`(text)), kindIs(kind))
            .orderBy(rank, similarity.desc(), COLLECTION_FEEDBACK.ID.desc())
            .limit(limit)
            .fetch { feedback(it, it[similarity]) }
    }

    override fun findByName(programId: Id, name: String, kinds: Collection<FeedbackKind>): List<CollectionFeedback> =
        select(null)
            .where(
                COLLECTION_FEEDBACK.PROGRAM_ID.eq(programId),
                COLLECTION_FEEDBACK.KIND.`in`(kinds.map { it.name }),
                DSL.lower(DSL.regexpReplaceAll(COLLECTION_FEEDBACK.SOURCE_TEXT, "\\s+", "")).eq(WatchEntry.normalize(name)),
            )
            .orderBy(COLLECTION_FEEDBACK.ID.desc())
            .fetch { feedback(it, null) }

    override fun deleteByWatchEntry(watchEntryId: Id) {
        dsl.deleteFrom(COLLECTION_FEEDBACK)
            .where(
                COLLECTION_FEEDBACK.KIND.`in`(FeedbackKind.WATCH_ALIAS.name, FeedbackKind.WATCH_EXCLUDE.name),
                DSL.field("({0} ->> 'watchEntryId')::bigint", Long::class.java, COLLECTION_FEEDBACK.CORRECTED_VALUE).eq(watchEntryId),
            )
            .execute()
    }

    private fun select(extra: Field<*>?) =
        dsl.select(COLLECTION_FEEDBACK.asterisk(), PROGRAMS.NAME)
            .apply { extra?.let { select(it) } }
            .from(COLLECTION_FEEDBACK)
            .leftJoin(PROGRAMS).on(PROGRAMS.ID.eq(COLLECTION_FEEDBACK.PROGRAM_ID))

    private fun kindIs(kind: FeedbackKind?): Condition = kind?.let { COLLECTION_FEEDBACK.KIND.eq(it.name) } ?: DSL.noCondition()

    private fun feedback(row: Record, similarity: Double?) = CollectionFeedback(
        id = row[COLLECTION_FEEDBACK.ID]!!,
        kind = FeedbackKind.valueOf(row[COLLECTION_FEEDBACK.KIND]!!),
        programId = row[COLLECTION_FEEDBACK.PROGRAM_ID],
        programName = row[PROGRAMS.NAME],
        host = row[COLLECTION_FEEDBACK.HOST],
        sourceText = row[COLLECTION_FEEDBACK.SOURCE_TEXT]!!,
        correctedValue = row[COLLECTION_FEEDBACK.CORRECTED_VALUE]!!.data(),
        reason = row[COLLECTION_FEEDBACK.REASON],
        activityId = row[COLLECTION_FEEDBACK.ACTIVITY_ID],
        createdAt = row[COLLECTION_FEEDBACK.CREATED_AT]!!,
        similarity = similarity,
    )

    private companion object {
        const val MAX_SOURCE = 1000
        const val MAX_REASON = 500
        val JSON: JsonMapper = JsonMapper.builder().findAndAddModules().build()

        fun host(url: String?): String? = url?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }
    }
}
