package com.dearby.nativeapp.features.calendar

import com.dearby.nativeapp.entities.activity.model.ScheduleModel
import com.dearby.nativeapp.entities.activity.model.instant

data class CalendarWindowState(val title: String, val start: Long, val end: Long, val zone: String)
/** One busy block from a calendar; [title] is shown on screen only and never stored or sent. */
data class BusyTimeState(val start: Long, val end: Long, val title: String? = null)
data class CalendarOverlapState(val activity: CalendarWindowState, val busy: BusyTimeState) {
    val start get() = maxOf(activity.start, busy.start)
    val end get() = minOf(activity.end, busy.end)
}
/** Where the comparison stands: asking for the permission, refused, or compared against the device calendar. */
sealed interface CalendarAccessState {
    data object Asking : CalendarAccessState
    data object Denied : CalendarAccessState
    data class Compared(val overlaps: List<CalendarOverlapState>) : CalendarAccessState
}
fun calendarOverlaps(window: CalendarWindowState, busy: BusyTimeState): Boolean =
    window.start < window.end && busy.start < busy.end && window.start < busy.end && busy.start < window.end
/** Offsets (`+09:00`) and `Z` both read (`OffsetDateTime`, not `Instant.parse`); an unreadable window is skipped. Date-only schedules arrive here already
 *  turned into their overlap-check windows (contract "겹침 확인 시간"). */
fun calendarWindows(schedules: List<ScheduleModel>): List<CalendarWindowState> = schedules.mapNotNull {
    val start = instant(it.startAt) ?: return@mapNotNull null
    val end = instant(it.endAt) ?: return@mapNotNull null
    CalendarWindowState(it.title, start.toEpochMilli(), end.toEpochMilli(), it.timeZone)
}
/** Every (activity window, busy block) pair that intersects, in schedule order then by the busy block's start. */
fun calendarOverlaps(windows: List<CalendarWindowState>, busy: List<BusyTimeState>): List<CalendarOverlapState> {
    val sorted = busy.sortedBy { it.start }
    return windows.flatMap { window -> sorted.filter { calendarOverlaps(window, it) }.map { CalendarOverlapState(window, it) } }
}
/** Compares the schedules against [source] over the span they cover. */
fun compareCalendar(schedules: List<ScheduleModel>, source: BusyTimeSource): List<CalendarOverlapState> {
    val windows = calendarWindows(schedules)
    if (windows.isEmpty()) return emptyList()
    return calendarOverlaps(windows, source.busyTimes(windows.minOf { it.start }, windows.maxOf { it.end }))
}
