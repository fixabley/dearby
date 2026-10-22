package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_STATUSES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ACTIVITY_SCHEDULE_STATUSES
import io.wid.dearby.application.activity.ProgressStatusRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.ProgressStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqProgressStatusRepository(private val dsl: DSLContext) : ProgressStatusRepository {

    @Transactional
    override fun replace(scheduleId: Id, statuses: List<ProgressStatus>) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val keep = statuses.mapNotNull { it.id }
        val removed = dsl.selectFrom(SCHEDULE_STATUSES)
            .where(SCHEDULE_STATUSES.SCHEDULE_ID.eq(scheduleId), SCHEDULE_STATUSES.ID.notIn(keep))
            .fetch()
        removed.forEach { row ->
            val picks = USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID.eq(row.id)
            if (row.kind == ProgressKind.OTHER.name) {
                dsl.deleteFrom(USER_ACTIVITY_SCHEDULE_STATUSES).where(picks).execute()
            } else {
                dsl.update(USER_ACTIVITY_SCHEDULE_STATUSES)
                    .setNull(USER_ACTIVITY_SCHEDULE_STATUSES.SCHEDULE_STATUS_ID)
                    .set(USER_ACTIVITY_SCHEDULE_STATUSES.DEFAULT_KIND, row.kind)
                    .set(USER_ACTIVITY_SCHEDULE_STATUSES.UPDATED_AT, now)
                    .where(picks)
                    .execute()
            }
        }
        dsl.deleteFrom(SCHEDULE_STATUSES).where(SCHEDULE_STATUSES.ID.`in`(removed.map { it.id })).execute()
        statuses.forEachIndexed { index, status ->
            if (status.id != null) {
                dsl.update(SCHEDULE_STATUSES)
                    .set(SCHEDULE_STATUSES.NAME, status.name)
                    .set(SCHEDULE_STATUSES.KIND, status.kind.name)
                    .set(SCHEDULE_STATUSES.SORT_ORDER, index)
                    .where(SCHEDULE_STATUSES.ID.eq(status.id), SCHEDULE_STATUSES.SCHEDULE_ID.eq(scheduleId))
                    .execute()
            } else {
                dsl.insertInto(SCHEDULE_STATUSES)
                    .set(SCHEDULE_STATUSES.SCHEDULE_ID, scheduleId)
                    .set(SCHEDULE_STATUSES.NAME, status.name)
                    .set(SCHEDULE_STATUSES.KIND, status.kind.name)
                    .set(SCHEDULE_STATUSES.SORT_ORDER, index)
                    .set(SCHEDULE_STATUSES.CREATED_AT, now)
                    .execute()
            }
        }
        // 수집한 세부 일정은 다시 수집할 때 지워지므로 운영자 지정으로 남긴다(모집·활동 기간 행은 제자리에서 고쳐져 id가 유지된다)
        dsl.update(SCHEDULES)
            .set(SCHEDULES.CURATED, true)
            .set(SCHEDULES.UPDATED_AT, LocalDateTime.now())
            .where(SCHEDULES.ID.eq(scheduleId), SCHEDULES.SLOT.isNull)
            .execute()
    }
}
