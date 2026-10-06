package com.dearby.nativeapp.features.calendar

import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import java.time.Instant

data class CalendarWindowState(val title: String, val start: Long, val end: Long, val zone: String)
data class BusyTimeState(val start: Long, val end: Long)
data class CalendarOverlapState(val activity: CalendarWindowState, val busy: BusyTimeState) {
    val start get() = maxOf(activity.start, busy.start)
    val end get() = minOf(activity.end, busy.end)
}
/** Where the overlap check is: asking, denied (offer settings) or compared. */
enum class CalendarPhase { IDLE, CHECKING, DENIED, COMPARED }

/** Touching ends do not overlap. */
fun calendarOverlaps(window: CalendarWindowState, busy: BusyTimeState): Boolean =
    window.start < window.end && busy.start < busy.end && window.start < busy.end && busy.start < window.end

fun calendarWindows(schedules: List<ScheduleModel>) = schedules.map {
    CalendarWindowState(it.title, Instant.parse(it.startAt).toEpochMilli(), Instant.parse(it.endAt).toEpochMilli(), it.timeZone)
}.filter { it.start < it.end }

/** Every (session, busy time) pair that shares time, in session then start order. */
fun calendarOverlaps(schedules: List<ScheduleModel>, busy: List<BusyTimeState>): List<CalendarOverlapState> =
    calendarWindows(schedules).flatMap { window -> busy.filter { calendarOverlaps(window, it) }.sortedBy { it.start }.map { CalendarOverlapState(window, it) } }
