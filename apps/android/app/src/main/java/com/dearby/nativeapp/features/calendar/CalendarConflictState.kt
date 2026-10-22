package com.dearby.nativeapp.features.calendar

import com.dearby.nativeapp.entities.activity.model.ScheduleModel
import com.dearby.nativeapp.entities.activity.model.instant
import java.time.Instant
import java.time.ZoneId

data class CalendarWindowState(val title: String, val start: Long, val end: Long, val zone: String)
/**
 * One busy block from a calendar; [title] is shown on screen only and never stored or sent. [event] is the device event it is an
 * occurrence of (an exception's original event) and [rule] that event's RRULE, for grouping (contract "반복 일정과 겹침 결과 묶음").
 */
data class BusyTimeState(val start: Long, val end: Long, val title: String? = null, val event: String? = null, val rule: String? = null)
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
/** Compares the schedules against [source] over the span they cover (a repeating schedule's windows are already expanded, at most 400). */
fun compareCalendar(schedules: List<ScheduleModel>, source: BusyTimeSource): List<CalendarOverlapState> {
    val windows = calendarWindows(schedules)
    if (windows.isEmpty()) return emptyList()
    return calendarOverlaps(windows, source.busyTimes(windows.minOf { it.start }, windows.maxOf { it.end }))
}

/**
 * One row of the result (contract "반복 일정과 겹침 결과 묶음"): the overlaps of one device event, earliest first. [rule] is the
 * event's RRULE; an event with several overlapping occurrences repeats even when its rule could not be read.
 */
data class OverlapGroupState(val title: String?, val rule: String?, val overlaps: List<CalendarOverlapState>) {
    val repeating get() = rule != null || overlaps.distinctBy { it.busy.start }.size > 1
}

/**
 * Groups [overlaps] by device event. The same event synced into several calendars (same title, start and end) counts once, under
 * the event seen first. Repeating groups come first, then one-off events, each by their first overlap.
 */
fun groupOverlaps(overlaps: List<CalendarOverlapState>): List<OverlapGroupState> {
    val owner = mutableMapOf<Triple<String?, Long, Long>, String>()
    val unique = overlaps.sortedBy { it.start }.distinctBy { Triple(it.activity, it.busy.title, it.busy.start to it.busy.end) }
    val groups = unique.groupBy { overlap -> owner.getOrPut(Triple(overlap.busy.title, overlap.busy.start, overlap.busy.end)) { overlap.busy.event ?: "${overlap.busy.start}" } }
    return groups.values.map { list -> OverlapGroupState(list.first().busy.title, list.firstNotNullOfOrNull { it.busy.rule }, list) }
        .sortedWith(compareBy({ !it.repeating }, { it.overlaps.first().start }))
}

/** The summary's day count: distinct days in [zone] an overlap starts on. */
fun overlapDays(overlaps: List<CalendarOverlapState>, zone: ZoneId): Int =
    overlaps.map { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.distinct().size
