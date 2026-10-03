package com.dearby.nativeapp.features.calendar

import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import java.time.Instant

data class CalendarWindowState(val title: String, val start: Long, val end: Long, val zone: String)
data class BusyTimeState(val start: Long, val end: Long)
data class CalendarOverlapState(val activity: CalendarWindowState, val busy: BusyTimeState) {
    val start get() = maxOf(activity.start, busy.start)
    val end get() = minOf(activity.end, busy.end)
}
val demoBusyTime = BusyTimeState(
    Instant.parse("2026-10-24T14:00:00+09:00").toEpochMilli(),
    Instant.parse("2026-10-24T15:00:00+09:00").toEpochMilli(),
)
fun calendarOverlaps(window: CalendarWindowState, busy: BusyTimeState): Boolean =
    window.start < window.end && busy.start < busy.end && window.start < busy.end && busy.start < window.end
fun demoOverlaps(schedules: List<ScheduleModel>): List<CalendarOverlapState> = schedules.map {
    CalendarWindowState(it.title, Instant.parse(it.startAt).toEpochMilli(), Instant.parse(it.endAt).toEpochMilli(), it.timeZone)
}.filter { calendarOverlaps(it, demoBusyTime) }.map { CalendarOverlapState(it, demoBusyTime) }
