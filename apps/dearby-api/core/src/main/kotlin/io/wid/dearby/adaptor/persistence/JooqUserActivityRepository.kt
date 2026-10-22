package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_STATUSES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ACTIVITY_SCHEDULE_STATUSES
import io.wid.dearby.application.useractivity.UserActivityRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.useractivity.SchedulePick
import io.wid.dearby.domain.useractivity.UserActivity
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqUserActivityRepository(private val dsl: DSLContext) : UserActivityRepository {

    override fun findAll(userId: UserId): List<UserActivity> = read(USER_ACTIVITIES.USER_ID.eq(userId))

    override fun find(userId: UserId, activityId: Id): UserActivity? =
        read(USER_ACTIVITIES.USER_ID.eq(userId).and(USER_ACTIVITIES.ACTIVITY_ID.eq(activityId))).singleOrNull()

    // 활동은 논리키라 left join으로 게시 여부를 본다. 선택은 한 번에 읽고, 어드민 상태가 사라진 선택은 뺀다
    private fun read(condition: Condition): List<UserActivity> {
        val rows = dsl.select(USER_ACTIVITIES.ID, USER_ACTIVITIES.ACTIVITY_ID, USER_ACTIVITIES.CREATED_AT, ACTIVITIES.ID)
            .from(USER_ACTIVITIES)
            .leftJoin(ACTIVITIES).on(ACTIVITIES.ID.eq(USER_ACTIVITIES.ACTIVITY_ID), ACTIVITIES.PUBLICATION_STATUS.eq(PublicationStatus.PUBLISHED.name))
            .where(condition)
            .orderBy(USER_ACTIVITIES.CREATED_AT, USER_ACTIVITIES.ID)
            .fetch()
        if (rows.isEmpty()) return emptyList()
        val picks = dsl.select(
            USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID, USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_ID,
            USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID, DSL.coalesce(SCHEDULE_STATUSES.KIND, USER_ACTIVITY_SCHEDULE_STATUSES.DEFAULT_KIND),
        )
            .from(USER_ACTIVITY_SCHEDULE_STATUSES)
            .leftJoin(SCHEDULE_STATUSES).on(SCHEDULE_STATUSES.ID.eq(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID))
            .where(USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID.`in`(rows.map { it.value1() }))
            .orderBy(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_ID)
            .fetch()
            .filter { it.value4() != null }
            .groupBy({ it.value1()!! }) { SchedulePick(it.value2()!!, it.value3(), ProgressKind.valueOf(it.value4()!!)) }
        return rows.map {
            UserActivity(it.value2()!!, it.value3()!!, deleted = it.value4() == null, statuses = picks[it.value1()].orEmpty())
        }
    }

    override fun insertIfAbsent(userId: UserId, activityId: Id): Boolean {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return dsl.insertInto(USER_ACTIVITIES)
            .set(USER_ACTIVITIES.ID, UUID.randomUUID())
            .set(USER_ACTIVITIES.USER_ID, userId)
            .set(USER_ACTIVITIES.ACTIVITY_ID, activityId)
            .set(USER_ACTIVITIES.CREATED_AT, now)
            .set(USER_ACTIVITIES.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute() > 0
    }

    @Transactional
    override fun delete(userId: UserId, activityId: Id) {
        val id = userActivityId(userId, activityId) ?: return
        dsl.deleteFrom(USER_ACTIVITY_SCHEDULE_STATUSES).where(USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID.eq(id)).execute()
        dsl.deleteFrom(USER_ACTIVITIES).where(USER_ACTIVITIES.ID.eq(id)).execute()
    }

    @Transactional
    override fun pick(userId: UserId, activityId: Id, scheduleId: Id, statusId: Id?, defaultKind: ProgressKind?) {
        val id = userActivityId(userId, activityId) ?: return
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(USER_ACTIVITY_SCHEDULE_STATUSES)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID, id)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_ID, scheduleId)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID, statusId)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.DEFAULT_KIND, defaultKind?.name)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.UPDATED_AT, now)
            .onConflict(USER_ACTIVITY_SCHEDULE_STATUSES.USER_ACTIVITY_ID, USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_ID).doUpdate()
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID, statusId)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.DEFAULT_KIND, defaultKind?.name)
            .set(USER_ACTIVITY_SCHEDULE_STATUSES.UPDATED_AT, now)
            .execute()
        dsl.update(USER_ACTIVITIES).set(USER_ACTIVITIES.UPDATED_AT, now).where(USER_ACTIVITIES.ID.eq(id)).execute()
    }

    private fun userActivityId(userId: UserId, activityId: Id): UUID? =
        dsl.select(USER_ACTIVITIES.ID).from(USER_ACTIVITIES)
            .where(USER_ACTIVITIES.USER_ID.eq(userId), USER_ACTIVITIES.ACTIVITY_ID.eq(activityId))
            .fetchOne(USER_ACTIVITIES.ID)
}
