package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

// 공개 API(api 모듈)의 ScheduleResponse와 같은 형태(admin-v1.md가 native-v1.md의 Schedule을 그대로 쓴다)
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
