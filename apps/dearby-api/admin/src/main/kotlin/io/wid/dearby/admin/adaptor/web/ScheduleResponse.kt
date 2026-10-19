package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.*
import java.time.LocalDate
import java.time.OffsetDateTime

// 공개 API(api 모듈)의 ScheduleResponse와 같은 형태(admin-v1.md가 native-v1.md의 Schedule을 그대로 쓴다) + curated
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
    val curated: Boolean, // 운영자가 직접 지정한 일정(admin-v1.md "일정 직접 지정")
    val curatedNote: String?, // 지정하며 남긴 사유(admin-v1.md "사유")
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
                curated = schedule.curated,
                curatedNote = schedule.curatedNote,
            )
        }
    }
}
