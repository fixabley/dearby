package io.wid.dearby.domain

import java.time.LocalDate
import java.time.ZonedDateTime

sealed interface ScheduleTime {
    // DTSTART;TZID=...  /  DTEND;TZID=... 하나만 있으면 시점 일정: start만 있으면 시작, end만 있으면 끝(마감)
    // ICS는 DTSTART가 필수라 start가 없으면 end를 DTSTART로 내보낸다
    data class Timed(val start: ZonedDateTime?, val end: ZonedDateTime?) : ScheduleTime {
        init {
            require(start != null || end != null) { "시작과 끝 중 하나는 있어야 합니다" }
            require(start == null || end == null || !end.isBefore(start)) { "끝은 시작보다 앞설 수 없습니다" }
        }
    }

    // DTSTART;VALUE=DATE  /  DTEND;VALUE=DATE (ICS 규칙대로 끝 날짜는 포함하지 않음)
    data class AllDay(val start: LocalDate, val endExclusive: LocalDate) : ScheduleTime
}
