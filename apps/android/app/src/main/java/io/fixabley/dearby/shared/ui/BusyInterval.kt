package io.fixabley.dearby.shared.ui

import java.time.Instant

/** Anonymous half-open interval; no event IDs or personal metadata. */
internal data class BusyInterval(val start: Instant, val end: Instant) {
    init { require(end > start) }
}
internal fun mergedBusy(intervals: List<BusyInterval>, window: BusyInterval): List<BusyInterval> {
    val clipped = intervals.mapNotNull {
        val start = maxOf(it.start, window.start); val end = minOf(it.end, window.end)
        if (end > start) BusyInterval(start, end) else null
    }.sortedBy { it.start }
    return clipped.fold(mutableListOf()) { result, next ->
        val last = result.lastOrNull()
        if (last != null && next.start <= last.end) result[result.lastIndex] = BusyInterval(last.start, maxOf(last.end, next.end))
        else result.add(next)
        result
    }
}
internal fun busyOverlaps(intervals: List<BusyInterval>, activity: BusyInterval): Boolean =
    intervals.any { it.start < activity.end && activity.start < it.end }
