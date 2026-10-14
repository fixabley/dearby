package io.wid.dearby.adaptor.web

import io.wid.dearby.domain.Address
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.ScheduleStatus
import io.wid.dearby.domain.ScheduleTime
import java.time.LocalDate
import java.time.OffsetDateTime

// 시각 일정: startAt·endAt(하나 이상)·timeZone. 종일 일정: startDate·endDateExclusive. 날짜 미정: 모두 null, dateLabel만
data class ScheduleResponse(
    val id: Long,
    val title: String,
    val kind: ScheduleKind,
    val status: ScheduleStatus,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val timeZone: String?,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
    val dateLabel: String,
    val address: Address?,
    val conferenceUrl: String?,
) {
    companion object {
        fun from(schedule: Schedule): ScheduleResponse {
            val timed = schedule.time as? ScheduleTime.Timed
            val allDay = schedule.time as? ScheduleTime.AllDay
            return ScheduleResponse(
                id = schedule.id,
                title = schedule.title,
                kind = schedule.kind,
                status = schedule.status,
                startAt = timed?.start?.toOffsetDateTime(),
                endAt = timed?.end?.toOffsetDateTime(),
                timeZone = (timed?.start ?: timed?.end)?.zone?.id,
                startDate = allDay?.start,
                endDateExclusive = allDay?.endExclusive,
                dateLabel = schedule.dateLabel,
                address = schedule.address,
                conferenceUrl = schedule.conferenceUrl,
            )
        }
    }
}
