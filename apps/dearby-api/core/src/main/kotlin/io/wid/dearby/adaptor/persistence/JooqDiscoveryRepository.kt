package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_EMBEDDINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISCOVERY_QUEUE
import io.wid.dearby.adaptor.persistence.jooq.tables.references.EMBEDDING_SETTINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECOMMENDATION_PREFERENCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECOMMENDATION_SIGNALS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_STATUSES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAG_EMBEDDINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ACTIVITY_SCHEDULE_STATUSES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_FAVORITES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ORG_AFFINITY
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_TASTE
import io.wid.dearby.application.discovery.DiscoveryRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.discovery.ActivityRef
import io.wid.dearby.domain.discovery.Engagement
import io.wid.dearby.domain.discovery.ImplicitSignal
import io.wid.dearby.domain.discovery.QueueRow
import io.wid.dearby.domain.discovery.Signal
import io.wid.dearby.domain.discovery.SignalKind
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqDiscoveryRepository(private val dsl: DSLContext) : DiscoveryRepository {

    private val json = JsonMapper.builder().build()

    override fun saveSignal(userId: UserId, activityId: Id, kind: SignalKind, tagId: Id?, at: OffsetDateTime) {
        dsl.insertInto(RECOMMENDATION_SIGNALS)
            .set(RECOMMENDATION_SIGNALS.USER_ID, userId)
            .set(RECOMMENDATION_SIGNALS.ACTIVITY_ID, activityId)
            .set(RECOMMENDATION_SIGNALS.KIND, kind.name)
            .set(RECOMMENDATION_SIGNALS.TAG_ID, tagId)
            .set(RECOMMENDATION_SIGNALS.CREATED_AT, at)
            .onConflict(RECOMMENDATION_SIGNALS.USER_ID, RECOMMENDATION_SIGNALS.ACTIVITY_ID, RECOMMENDATION_SIGNALS.KIND).doUpdate()
            .set(RECOMMENDATION_SIGNALS.TAG_ID, tagId)
            .set(RECOMMENDATION_SIGNALS.CREATED_AT, at)
            .execute()
    }

    override fun deleteSignal(userId: UserId, activityId: Id, kind: SignalKind) {
        dsl.deleteFrom(RECOMMENDATION_SIGNALS)
            .where(RECOMMENDATION_SIGNALS.USER_ID.eq(userId), RECOMMENDATION_SIGNALS.ACTIVITY_ID.eq(activityId), RECOMMENDATION_SIGNALS.KIND.eq(kind.name))
            .execute()
    }

    override fun signals(userId: UserId): List<Signal> =
        dsl.selectFrom(RECOMMENDATION_SIGNALS).where(RECOMMENDATION_SIGNALS.USER_ID.eq(userId)).fetch()
            .mapNotNull { row -> SignalKind.entries.firstOrNull { it.name == row.kind }?.let { Signal(row.activityId!!, it, row.tagId, row.createdAt!!) } }

    // 지원 = 모집 기간 일정 PASSED 또는 어디든 탈락(지원은 한 것), 참여 = 활동 기간 일정에 탈락 아닌 상태가 있고 탈락이 없음
    override fun implicitSignals(userId: UserId): List<ImplicitSignal> {
        val kind = DSL.coalesce(SCHEDULE_STATUSES.KIND, USER_ACTIVITY_SCHEDULE_STATUSES.DEFAULT_KIND)
        val applied = DSL.boolOr(SCHEDULES.SLOT.eq(SLOT_APPLICATION).and(kind.eq(ProgressKind.PASSED.name)))
        val failed = DSL.boolOr(kind.eq(ProgressKind.FAILED.name))
        val reached = DSL.boolOr(SCHEDULES.SLOT.eq(SLOT_ACTIVITY).and(kind.ne(ProgressKind.FAILED.name)))
        return dsl.select(USER_ACTIVITIES.ACTIVITY_ID, ACTIVITIES.PROGRAM_ID, USER_ACTIVITIES.UPDATED_AT, applied, failed, reached)
            .from(USER_ACTIVITIES)
            .join(ACTIVITIES).on(ACTIVITIES.ID.eq(USER_ACTIVITIES.ACTIVITY_ID))
            .leftJoin(USER_ACTIVITY_SCHEDULE_STATUSES).on(USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID.eq(USER_ACTIVITIES.ID))
            .leftJoin(SCHEDULE_STATUSES).on(SCHEDULE_STATUSES.ID.eq(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID))
            .leftJoin(SCHEDULES).on(SCHEDULES.ID.eq(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_ID))
            .where(USER_ACTIVITIES.USER_ID.eq(userId))
            .groupBy(USER_ACTIVITIES.ACTIVITY_ID, ACTIVITIES.PROGRAM_ID, USER_ACTIVITIES.UPDATED_AT)
            .fetch { row ->
                val strength = when {
                    row.value6() == true && row.value5() != true -> Engagement.PARTICIPATED
                    row.value4() == true || row.value5() == true -> Engagement.APPLIED
                    else -> Engagement.ADDED
                }
                ImplicitSignal(row.value1()!!, row.value2()!!, strength, row.value3()!!)
            }
    }

    override fun activityRefs(activityIds: Collection<Id>): Map<Id, ActivityRef> {
        if (activityIds.isEmpty()) return emptyMap()
        return dsl.select(ACTIVITIES.ID, ACTIVITIES.PROGRAM_ID, PROGRAMS.ORGANIZATION_ID, ACTIVITIES.NAME)
            .from(ACTIVITIES).join(PROGRAMS).on(PROGRAMS.ID.eq(ACTIVITIES.PROGRAM_ID))
            .where(ACTIVITIES.ID.`in`(activityIds))
            .fetch { ActivityRef(it.value1()!!, it.value2()!!, it.value3()!!, it.value4()!!) }
            .associateBy { it.activityId }
    }

    override fun queueUsers(): List<UserId> =
        dsl.select(RECOMMENDATION_PREFERENCES.USER_ID).from(RECOMMENDATION_PREFERENCES)
            .union(DSL.select(RECOMMENDATION_SIGNALS.USER_ID).from(RECOMMENDATION_SIGNALS))
            .union(DSL.select(USER_ACTIVITIES.USER_ID).from(USER_ACTIVITIES))
            .union(DSL.select(DISCOVERY_QUEUE.USER_ID).from(DISCOVERY_QUEUE))
            .union(DSL.select(USER_FAVORITES.USER_ID).from(USER_FAVORITES))
            .fetch { it.value1()!! }

    override fun activeModel(): String? = dsl.select(EMBEDDING_SETTINGS.ACTIVE_MODEL).from(EMBEDDING_SETTINGS).fetchOne()?.value1()

    override fun activityVectors(activityIds: Collection<Id>, model: String): Map<Id, FloatArray> {
        if (activityIds.isEmpty()) return emptyMap()
        return dsl.select(ACTIVITY_EMBEDDINGS.ACTIVITY_ID, VectorRows.read(ACTIVITY_EMBEDDINGS.EMBEDDING))
            .from(ACTIVITY_EMBEDDINGS)
            .where(ACTIVITY_EMBEDDINGS.MODEL.eq(model), ACTIVITY_EMBEDDINGS.ACTIVITY_ID.`in`(activityIds))
            .fetch().associate { it.value1()!! to VectorRows.floats(it.value2())!! }
    }

    override fun tagVectors(tagIds: Collection<Id>, model: String): Map<Id, FloatArray> {
        if (tagIds.isEmpty()) return emptyMap()
        return dsl.select(TAG_EMBEDDINGS.TAG_ID, VectorRows.read(TAG_EMBEDDINGS.EMBEDDING))
            .from(TAG_EMBEDDINGS)
            .where(TAG_EMBEDDINGS.MODEL.eq(model), TAG_EMBEDDINGS.TAG_ID.`in`(tagIds))
            .fetch().associate { it.value1()!! to VectorRows.floats(it.value2())!! }
    }

    override fun saveTaste(userId: UserId, model: String?, vector: FloatArray?) {
        if (model == null || vector == null) {
            dsl.deleteFrom(USER_TASTE).where(USER_TASTE.USER_ID.eq(userId)).execute()
            return
        }
        dsl.execute(
            """
            insert into user_taste (user_id, model, embedding, updated_at) values (?, ?, cast(? as halfvec), now())
            on conflict (user_id) do update set model = excluded.model, embedding = excluded.embedding, updated_at = excluded.updated_at
            """.trimIndent(),
            userId, model, VectorRows.text(vector),
        )
    }

    override fun saveAffinity(userId: UserId, scores: Map<Id, Double>) {
        dsl.deleteFrom(USER_ORG_AFFINITY).where(USER_ORG_AFFINITY.USER_ID.eq(userId)).execute()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val rows = scores.filterValues { it > 0 }.map { (organizationId, score) ->
            dsl.newRecord(USER_ORG_AFFINITY).apply {
                this.userId = userId
                this.organizationId = organizationId
                this.score = score.coerceIn(0.0, 1.0).toFloat()
                updatedAt = now
            }
        }
        if (rows.isNotEmpty()) dsl.batchInsert(rows).execute()
    }

    override fun queue(userId: UserId, date: LocalDate): List<QueueRow> =
        dsl.selectFrom(DISCOVERY_QUEUE)
            .where(DISCOVERY_QUEUE.USER_ID.eq(userId), DISCOVERY_QUEUE.QUEUE_DATE.eq(date))
            .orderBy(DISCOVERY_QUEUE.POSITION)
            .fetch { QueueRow(it.position!!.toInt(), it.activityId!!, json.readValue<List<String>>(it.reasons!!.data()), it.generatedAt!!) }

    override fun replaceQueue(userId: UserId, date: LocalDate, rows: List<QueueRow>) {
        dsl.deleteFrom(DISCOVERY_QUEUE).where(DISCOVERY_QUEUE.USER_ID.eq(userId)).execute()
        val records = rows.map { row ->
            dsl.newRecord(DISCOVERY_QUEUE).apply {
                this.userId = userId
                queueDate = date
                position = row.position.toShort()
                activityId = row.activityId
                reasons = JSONB.valueOf(json.writeValueAsString(row.reasons))
                generatedAt = row.generatedAt
            }
        }
        if (records.isNotEmpty()) dsl.batchInsert(records).execute()
    }

    private companion object {
        const val SLOT_APPLICATION = "APPLICATION"
        const val SLOT_ACTIVITY = "ACTIVITY"
    }
}
