package io.fixabley.dearby.shared.ui

/** Exact half-open intersection; drawing minimum heights never participate. */
internal fun timelineIntersection(first: BusyInterval, second: BusyInterval): BusyInterval? {
    val start = maxOf(first.start, second.start)
    val end = minOf(first.end, second.end)
    return if (end > start) BusyInterval(start, end) else null
}
