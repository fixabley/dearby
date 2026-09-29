package com.dearby.nativeapp

import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.features.calendar.calendarWindow
import com.dearby.nativeapp.features.calendar.calendarOverlaps
import com.dearby.nativeapp.features.calendar.CalendarOverlapState
import com.dearby.nativeapp.shared.calendar.DeviceBusyTime
import com.dearby.nativeapp.shared.calendar.calendarBusyTime
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId

class CalendarConflictTest {
    private fun schedule(start: String? = "2026-10-24T14:00:00+09:00", end: String? = "2026-10-24T16:00:00+09:00", zone: String = "Asia/Seoul") = ScheduleModel("s", "활동", start, end, "", zone)
    @Test fun halfOpenIntervalsExcludeTouchingAndZeroLength() {
        val window = calendarWindow(schedule())!!
        assertFalse(calendarOverlaps(window, DeviceBusyTime(window.end, window.end + 1)))
        assertFalse(calendarOverlaps(window, DeviceBusyTime(window.start - 1, window.start)))
        assertFalse(calendarOverlaps(window, DeviceBusyTime(window.start, window.start)))
        val busy = DeviceBusyTime(window.start + 3_600_000, window.end + 3_600_000)
        assertTrue(calendarOverlaps(window, busy))
        assertEquals(window.end, CalendarOverlapState(window, busy).end)
        assertEquals(busy.start, CalendarOverlapState(window, busy).start)
    }
    @Test fun unknownAndInvalidIntervalsNeverBecomeNoConflict() {
        listOf(schedule(start = null), schedule(end = null), schedule(end = "invalid"), schedule(end = "2026-10-24T13:00:00+09:00"), schedule(zone = "invalid")).forEach { assertNull(calendarWindow(it)) }
    }
    @Test fun explicitOffsetsSpanDSTFallBack() {
        val window = calendarWindow(schedule("2026-11-01T01:30:00-04:00", "2026-11-01T01:30:00-05:00", "America/New_York"))!!
        assertEquals(3_600_000L, window.end - window.start)
    }
    @Test fun allDayDatesUseDeviceZoneAcrossShortDSTDay() {
        val start = Instant.parse("2026-03-08T00:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-03-09T00:00:00Z").toEpochMilli()
        val time = calendarBusyTime(start, end, true, ZoneId.of("America/New_York"))
        assertEquals(23 * 3_600_000L, time.end - time.start)
        assertEquals(Instant.parse("2026-03-08T05:00:00Z").toEpochMilli(), time.start)
        assertEquals(DeviceBusyTime(start, end), calendarBusyTime(start, end, false, ZoneId.of("Asia/Seoul")))
    }
}
