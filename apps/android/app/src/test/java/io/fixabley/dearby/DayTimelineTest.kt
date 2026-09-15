package io.fixabley.dearby

import io.fixabley.dearby.pages.noticedetail.model.detailTimeline
import io.fixabley.dearby.shared.ui.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DayTimelineTest {
    private fun interval(s: String, e: String, z: String = "Asia/Seoul") = TimelineInterval(
        OffsetDateTime.parse(s).toInstant(), OffsetDateTime.parse(e).toInstant(), ZoneId.of(z))
    @Test fun multiDayIsContinuousAndExclusiveMidnightDoesNotCreateAnotherDay() {
        val range = interval("2026-08-27T09:00:00+09:00", "2026-09-15T13:00:00+09:00")
        val first = timelineDay(range, range.firstDate)!!
        assertEquals(540f, first.startMinute); assertEquals(1440f, first.endMinute)
        val middle = timelineDay(range, LocalDate.parse("2026-09-01"))!!
        assertEquals(0f, middle.startMinute); assertEquals(1440f, middle.endMinute)
        val last = timelineDay(range, range.lastDate)!!
        assertEquals(0f, last.startMinute); assertEquals(780f, last.endMinute)
        assertNull(timelineDay(range, range.lastDate.plusDays(1)))
        assertNull(range.adjacent(range.firstDate, -1)); assertNull(range.adjacent(range.lastDate, 1))
        val midnight = interval("2026-12-31T23:00:00+09:00", "2027-01-01T00:00:00+09:00")
        assertEquals(LocalDate.parse("2026-12-31"), midnight.lastDate)
    }
    @Test fun dstUsesLocalCalendarDatesWithActualShortAndLongDays() {
        val spring = interval("2026-03-08T00:00:00-05:00", "2026-03-09T00:00:00-04:00", "America/New_York")
        val day = timelineDay(spring, spring.firstDate)!!
        assertEquals(1380f, day.minutes)
        assertFalse(day.ticks.any { it.label.startsWith("오전 2시") })
        val fall = interval("2026-11-01T00:00:00-04:00", "2026-11-02T00:00:00-05:00", "America/New_York")
        val repeated = timelineDay(fall, fall.firstDate)!!
        assertEquals(1500f, repeated.minutes)
        assertEquals(2, repeated.ticks.count { it.label.startsWith("오전 1시\n") })
        assertTrue(repeated.ticks.any { it.label.endsWith("-04:00") })
        assertTrue(repeated.ticks.any { it.label.endsWith("-05:00") })
    }
    @Test fun unknownPrecisionInvalidAndConflictNeverMakeBars() {
        assertNull(detailTimeline(null, "2026-09-15", null, "2026-09-16", "Asia/Seoul"))
        assertNull(detailTimeline(null, null, "2026-09-15T13:00:00+09:00", null, "Asia/Seoul"))
        assertNull(detailTimeline("2026-09-15T09:00:00+09:00", null, null, "2026-09-15", "Asia/Seoul"))
        assertNull(detailTimeline("invalid", null, "2026-09-15T13:00:00+09:00", null, "Asia/Seoul"))
        assertNull(detailTimeline("2026-09-15T09:00:00+09:00", "2026-09-16", "2026-09-15T13:00:00+09:00", null, "Asia/Seoul"))
        assertNull(detailTimeline("2026-09-16T09:00:00+09:00", null, "2026-09-15T13:00:00+09:00", null, "Asia/Seoul"))
    }
    @Test fun shortDurationKeepsExactSemanticsAndSkippedDateNavigationIsSafe() {
        val short = interval("2026-09-15T23:59:58+09:00", "2026-09-15T23:59:59.5+09:00")
        val day = timelineDay(short, short.firstDate)!!
        assertEquals(0.025f, day.endMinute - day.startMinute, 0.001f)
        assertTrue(day.description.contains("59.5초"))
        val skipped = interval("2011-12-29T09:00:00-10:00", "2011-12-31T13:00:00+14:00", "Pacific/Apia")
        assertFalse(skipped.contains(LocalDate.parse("2011-12-30")))
        assertEquals(LocalDate.parse("2011-12-31"), skipped.adjacent(skipped.firstDate, 1))
    }
}
