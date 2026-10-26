package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_SESSIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_SESSION_FAVORITES
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.application.card.ExchangeContextRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeRef
import io.wid.dearby.domain.timetable.TimetableRules
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.OffsetDateTime

// 저장 시각 기준 한 번만 판정한다. 게시 여부는 보지 않는다(활동 id만 있으면 되는 기존 규칙과 같다)
@Repository
class JooqExchangeContextRepository(private val dsl: DSLContext) : ExchangeContextRepository {

    override fun locate(activityId: Id, recipientId: UserId?, now: OffsetDateTime): ExchangeRef? {
        if (!dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))) return null
        recipientId?.let { currentFavorite(activityId, it, now) }?.let { return it }
        return ExchangeRef(activityId, currentBlock(activityId, now), null)
    }

    // 받는 사람이 찜한 세션 중 지금 진행 중인 것(지운 블록 제외). 다른 트랙에 같은 시간 찜이 겹치면 시작이 빠른 것, 같으면 id 순
    private fun currentFavorite(activityId: Id, recipientId: UserId, now: OffsetDateTime): ExchangeRef? =
        dsl.select(SCHEDULE_SESSIONS.SCHEDULE_ID, SCHEDULE_SESSIONS.ID).from(USER_SESSION_FAVORITES)
            .join(SCHEDULE_SESSIONS).on(SCHEDULE_SESSIONS.ID.eq(USER_SESSION_FAVORITES.SESSION_ID))
            .join(SCHEDULES).on(SCHEDULES.ID.eq(SCHEDULE_SESSIONS.SCHEDULE_ID))
            .where(
                USER_SESSION_FAVORITES.USER_ID.eq(recipientId), SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED_DELETED.isFalse,
                SCHEDULE_SESSIONS.START_AT.le(now), SCHEDULE_SESSIONS.END_AT.gt(now),
            )
            .orderBy(SCHEDULE_SESSIONS.START_AT, SCHEDULE_SESSIONS.ID).limit(1)
            .fetchOne { ExchangeRef(activityId, it.value1(), it.value2()) }

    // 지금 진행 중인 블록: 시간표를 붙일 수 있는 블록(시작·끝이 있는 시각 일정 또는 종일 일정, TimetableRules.window)의 창 안에 now가 있는 것.
    // 활동 기간이 세부 일정을 품어 둘 다 맞으면 짧은 것(세부 일정) 하나
    private fun currentBlock(activityId: Id, now: OffsetDateTime): Id? =
        dsl.selectFrom(SCHEDULES)
            .where(SCHEDULES.ACTIVITY_ID.eq(activityId), SCHEDULES.CURATED_DELETED.isFalse, SCHEDULES.SLOT.isDistinctFrom(ScheduleSlot.APPLICATION.name))
            .fetch { it.toSchedule() }
            .mapNotNull { schedule ->
                val (from, to) = try { TimetableRules.window(schedule) } catch (_: InvalidInputException) { return@mapNotNull null }
                if (!from.isAfter(now) && to.isAfter(now)) schedule.id to Duration.between(from, to) else null
            }
            .minWithOrNull(compareBy({ it.second }, { it.first }))?.first
}
