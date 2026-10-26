package io.wid.dearby.adaptor.web.v1.activities

import io.wid.dearby.domain.*
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
    val statuses: List<ProgressStatusResponse>, // 고를 수 있는 진행 상태(어드민 지정 또는 기본 세 개, id null)
    val overlapCheck: OverlapCheckResponse?, // 겹침 확인 시간(서울 HH:mm). 없으면 null
    val recurrence: String?, // 반복 규칙(RRULE 본문, 접두사 없음). 없으면 null
    val hasTimetable: Boolean, // 하위 시간표가 있으면 true(앱이 "○○ 시간표" 버튼을 보인다, native-v1.md "트랙·세션 시간표")
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
                statuses = schedule.statuses.map { ProgressStatusResponse(it.id, it.name, it.kind) },
                overlapCheck = schedule.overlapCheck?.let(OverlapCheckResponse::from),
                recurrence = schedule.recurrence?.rule,
                hasTimetable = schedule.hasTimetable,
            )
        }
    }
}

data class ProgressStatusResponse(val id: Long?, val name: String, val kind: ProgressKind)

data class OverlapCheckResponse(val startTime: String, val endTime: String) {
    companion object {
        private val HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        fun from(check: OverlapCheck) = OverlapCheckResponse(check.startTime.format(HH_MM), check.endTime.format(HH_MM))
    }
}
