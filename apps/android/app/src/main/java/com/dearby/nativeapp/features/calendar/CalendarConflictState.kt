package com.dearby.nativeapp.features.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.shared.calendar.DeviceBusyTime
import com.dearby.nativeapp.shared.calendar.DeviceCalendarOption
import com.dearby.nativeapp.shared.calendar.DeviceCalendarStore
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

data class CalendarWindowState(val title: String, val start: Long, val end: Long, val zone: String)
data class CalendarOverlapState(val activity: CalendarWindowState, val busy: DeviceBusyTime) {
    val start get() = maxOf(activity.start, busy.start)
    val end get() = minOf(activity.end, busy.end)
}
fun calendarWindow(schedule: ScheduleModel): CalendarWindowState? = runCatching {
    val start = Instant.parse(schedule.startAt).toEpochMilli()
    val end = Instant.parse(schedule.endAt).toEpochMilli()
    ZoneId.of(schedule.timeZone)
    require(start < end)
    CalendarWindowState(schedule.title, start, end, schedule.timeZone)
}.getOrNull()
fun calendarOverlaps(window: CalendarWindowState, busy: DeviceBusyTime): Boolean =
    busy.start < busy.end && window.start < busy.end && busy.start < window.end

class CalendarConflictState(schedules: List<ScheduleModel>, private val store: DeviceCalendarStore) {
    val windows = schedules.mapNotNull(::calendarWindow)
    val unknownCount = if (schedules.isEmpty()) 1 else schedules.size - windows.size
    var phase by mutableStateOf("idle"); private set
    var calendars by mutableStateOf(emptyList<DeviceCalendarOption>()); private set
    var selected by mutableStateOf(emptySet<Long>()); private set
    var overlaps by mutableStateOf(emptyList<CalendarOverlapState>()); private set
    var index by mutableStateOf(0)
    private var generation = 0
    fun clear() { generation++; calendars = emptyList(); selected = emptySet(); overlaps = emptyList(); index = 0; phase = "idle" }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id; overlaps = emptyList(); index = 0; phase = "choose" }
    suspend fun connect() {
        clear()
        if (windows.isEmpty()) { phase = "unknown"; return }
        if (!store.authorized()) { phase = "permission"; return }
        val ticket = generation
        phase = "loading"
        try {
            val choices = store.calendars()
            if (ticket != generation) return
            calendars = choices; selected = choices.map { it.id }.toSet()
            phase = if (choices.isEmpty()) "no_calendars" else "choose"
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (ticket == generation) phase = if (error is SecurityException) "permission" else "failed" }
    }
    suspend fun compare() {
        if (selected.isEmpty()) { phase = "choose"; return }
        val ticket = ++generation
        overlaps = emptyList(); index = 0; phase = "loading"
        try {
            val available = store.calendars().map { it.id }.toSet()
            check(available.containsAll(selected))
            val found = windows.flatMap { window -> store.busy(window.start, window.end, selected).distinct()
                .filter { calendarOverlaps(window, it) }.map { CalendarOverlapState(window, it) } }.sortedBy { it.start }
            if (ticket == generation) { overlaps = found; phase = "result" }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { if (ticket == generation) { overlaps = emptyList(); phase = if (error is SecurityException) "permission" else "failed" } }
    }
}
