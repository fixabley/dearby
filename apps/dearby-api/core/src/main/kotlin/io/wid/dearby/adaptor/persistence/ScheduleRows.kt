package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.SchedulesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.SCHEDULE_STATUSES
import org.jooq.DSLContext
import io.wid.dearby.domain.*
import java.time.ZoneId

// schedules 행 → 도메인 Schedule. 활동 조회와 세부 일정 편집기가 같은 변환을 쓴다
// statuses: 그 일정의 어드민 상태(sort_order 순). 비면 기본 세 개
internal fun SchedulesRecord.toSchedule(statuses: List<ProgressStatus> = emptyList(), hasTimetable: Boolean = false) = Schedule(
    id = id!!,
    title = title!!,
    kind = ScheduleKind.valueOf(kind!!),
    status = ScheduleStatus.valueOf(status!!),
    time = when (timeType) {
        "TIMED" -> {
            val zone = ZoneId.of(timeZone!!)
            ScheduleTime.Timed(startAt?.atZoneSameInstant(zone), endAt?.atZoneSameInstant(zone))
        }
        "ALL_DAY" -> ScheduleTime.AllDay(startDate!!, endDateExclusive!!)
        else -> null
    },
    dateLabel = dateLabel!!,
    address = lat?.let { Address(it, lng!!, addressLine1!!, addressLine2!!) },
    conferenceUrl = conferenceUrl,
    sequence = sequence!!,
    timestamps = Timestamps(createdAt!!, updatedAt!!),
    curatedNote = curatedNote,
    curated = curated ?: false,
    overlapCheck = overlapStartTime?.let { OverlapCheck(it, overlapEndTime!!) },
    statuses = statuses.ifEmpty { ProgressStatus.DEFAULTS },
    recurrence = recurrence?.let(::Recurrence),
    hasTimetable = hasTimetable,
)

// 일정들의 어드민 상태를 한 번에 읽는다(일정 id → sort_order 순 목록)
internal fun progressStatusesOf(dsl: DSLContext, scheduleIds: Collection<Id>): Map<Id, List<ProgressStatus>> {
    if (scheduleIds.isEmpty()) return emptyMap()
    return dsl.selectFrom(SCHEDULE_STATUSES)
        .where(SCHEDULE_STATUSES.SCHEDULE_ID.`in`(scheduleIds))
        .orderBy(SCHEDULE_STATUSES.SORT_ORDER, SCHEDULE_STATUSES.ID)
        .fetch()
        .groupBy({ it.scheduleId!! }) { ProgressStatus(it.id!!, it.name!!, ProgressKind.valueOf(it.kind!!)) }
}
