package io.fixabley.dearby

import io.fixabley.dearby.shared.ui.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TimelineIntersectionTest {
    private fun at(seconds: Long) = Instant.EPOCH.plusSeconds(seconds)
    @Test fun partialAndContainingIntervalsClipOnlyTheirSharedTime() {
        val activity = BusyInterval(at(9), at(12))
        assertEquals(BusyInterval(at(9), at(10)), timelineIntersection(activity, BusyInterval(at(8), at(10))))
        assertEquals(activity, timelineIntersection(activity, BusyInterval(at(8), at(13))))
        assertEquals(BusyInterval(at(10), at(11)), timelineIntersection(activity, BusyInterval(at(10), at(11))))
    }
    @Test fun disjointAndTouchDoNotIntersectButNanosecondOverlapDoes() {
        val activity = BusyInterval(at(9), at(12))
        assertNull(timelineIntersection(activity, BusyInterval(at(12), at(13))))
        assertNull(timelineIntersection(activity, BusyInterval(at(13), at(14))))
        val short = BusyInterval(at(12).minusNanos(1), at(12))
        assertEquals(short, timelineIntersection(activity, short))
    }
    @Test fun clippingAndUnionPreserveIntersectionAcrossMidnight() {
        val window = BusyInterval(at(0), at(86400))
        val union = mergedBusy(listOf(BusyInterval(at(-3600), at(3600)), BusyInterval(at(1800), at(7200))), window)
        assertEquals(listOf(BusyInterval(at(0), at(7200))), union)
        assertEquals(BusyInterval(at(0), at(3600)), timelineIntersection(union.single(), BusyInterval(at(-1), at(3600))))
    }
}
