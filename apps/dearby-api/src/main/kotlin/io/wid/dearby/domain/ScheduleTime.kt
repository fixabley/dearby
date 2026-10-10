package io.wid.dearby.domain

import java.time.LocalDate
import java.time.ZonedDateTime

sealed interface ScheduleTime {
    // DTSTART;TZID=...  /  DTEND;TZID=... (end가 없으면 시점 일정)
    data class Timed(val start: ZonedDateTime, val end: ZonedDateTime?) : ScheduleTime

    // DTSTART;VALUE=DATE  /  DTEND;VALUE=DATE (ICS 규칙대로 끝 날짜는 포함하지 않음)
    data class AllDay(val start: LocalDate, val endExclusive: LocalDate) : ScheduleTime
}
