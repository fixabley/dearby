package io.fixabley.dearby.features.calendarbusy

import io.fixabley.dearby.features.calendarbusy.model.BusyOccurrence
import io.fixabley.dearby.shared.ui.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class BusyIntervalTest {
    private fun at(hour: Long) = Instant.EPOCH.plusSeconds(hour * 3600)
    @Test fun clipsMergesAndExcludesTouchOnlyOverlap() {
        val activity = BusyInterval(at(9), at(11))
        assertFalse(busyOverlaps(listOf(BusyInterval(at(11), at(12))), activity))
        assertTrue(busyOverlaps(listOf(BusyInterval(at(10), at(12))), activity))
        assertEquals(listOf(BusyInterval(at(9), at(13))), mergedBusy(listOf(
            BusyInterval(at(8), at(10)), BusyInterval(at(10), at(12)), BusyInterval(at(11), at(14))), BusyInterval(at(9), at(13))))
    }
    @Test fun floatingAllDayUsesDeviceMidnightAndDST() {
        fun occurrence(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val row = BusyOccurrence(occurrence("2026-03-08"), occurrence("2026-03-09"), true, 0, 0, 0)
        val seoul = row.interval(ZoneId.of("Asia/Seoul"))!!
        assertEquals(0, seoul.start.atZone(ZoneId.of("Asia/Seoul")).hour)
        val la = row.interval(ZoneId.of("America/Los_Angeles"))!!
        assertEquals(23, Duration.between(la.start, la.end).toHours())
        val fall = BusyOccurrence(occurrence("2026-11-01"), occurrence("2026-11-02"), true, 0, 0, 0)
        assertEquals(25, fall.interval(ZoneId.of("America/Los_Angeles"))!!.let { Duration.between(it.start, it.end).toHours() })
    }
    @Test fun onlyExplicitFreeCancelledDeclinedAreExcluded() {
        val row = BusyOccurrence(0, 3600000, false, 2, 0, 0)
        assertNotNull(row.interval(ZoneOffset.UTC))
        assertNull(row.copy(availability = 1).interval(ZoneOffset.UTC))
        assertNull(row.copy(status = 2).interval(ZoneOffset.UTC))
        assertNull(row.copy(selfStatus = 2).interval(ZoneOffset.UTC))
        assertNull(row.copy(end = 0).interval(ZoneOffset.UTC))
    }
    @Test fun floatingDeviceDayClipsToDifferentSourceZoneWithoutNineAmShift() {
        val row = BusyOccurrence(Instant.parse("2026-03-08T00:00:00Z").toEpochMilli(),
            Instant.parse("2026-03-09T00:00:00Z").toEpochMilli(), true, 0, 0, 0)
        val value = row.interval(ZoneId.of("America/Los_Angeles"))!!
        val source = BusyInterval(Instant.parse("2026-03-08T15:00:00Z"), Instant.parse("2026-03-09T15:00:00Z"))
        assertEquals(listOf(BusyInterval(source.start, Instant.parse("2026-03-09T07:00:00Z"))), mergedBusy(listOf(value), source))
        assertTrue(mergedBusy(listOf(value), BusyInterval(value.end, value.end.plusSeconds(86400))).isEmpty())
    }

}
