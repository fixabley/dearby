package com.dearby.nativeapp

import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.entities.catalog.model.safeHttpsUrl
import com.dearby.nativeapp.features.calendar.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PrototypeTest {
    @Test fun conferenceHasOneHourConflictOthersHaveNone() {
        val conflict = demoOverlaps(listOf(ScheduleModel("컨퍼런스", "2026-10-24T13:00:00+09:00", "2026-10-24T17:00:00+09:00"))).single()
        assertEquals(Instant.parse("2026-10-24T05:00:00Z").toEpochMilli(), conflict.start)
        assertEquals(3_600_000L, conflict.end - conflict.start)
        assertTrue(demoOverlaps(listOf(ScheduleModel("캠프", "2026-11-07T10:00:00+09:00", "2026-11-07T18:00:00+09:00"))).isEmpty())
    }
    @Test fun touchingOrEmptyIntervalsDoNotOverlap() {
        val window = CalendarWindowState("test", 10, 20, "Asia/Seoul")
        assertFalse(calendarOverlaps(window, BusyTimeState(20, 30)))
        assertFalse(calendarOverlaps(window, BusyTimeState(0, 10)))
        assertFalse(calendarOverlaps(window, BusyTimeState(15, 15)))
        assertTrue(calendarOverlaps(window, BusyTimeState(15, 30)))
    }
    @Test fun applicationLinksAreHttpsOnly() {
        assertNotNull(safeHttpsUrl("https://example.com/dearby/camp"))
        listOf("http://example.com", "javascript:alert(1)", "file:///data/local", "dearby://card/1", "https://user:pw@example.com", "https://")
            .forEach { assertNull(it, safeHttpsUrl(it)) }
        assertNull(safeHttpsUrl(null))
    }
}
