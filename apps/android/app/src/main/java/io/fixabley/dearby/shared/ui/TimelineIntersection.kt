package io.fixabley.dearby.shared.ui

/** Exact half-open intersection; drawing minimum heights never participate. */
internal fun timelineIntersection(first: BusyInterval, second: BusyInterval): BusyInterval? {
    val start = maxOf(first.start, second.start)
    val end = minOf(first.end, second.end)
    return if (end > start) BusyInterval(start, end) else null
}

internal fun timelineIntersectionText(interval: BusyInterval, zone: java.time.ZoneId, dayEnd: java.time.Instant): String {
    val start = interval.start.atZone(zone)
    val end = interval.end.atZone(zone)
    val endText = if (interval.end == dayEnd) "다음 날 오전 12시" else koreanTime(end.toLocalTime())
    val offsets = if (start.offset != end.offset) " (${start.offset} → ${end.offset})" else ""
    return "${koreanTime(start.toLocalTime())}부터 ${endText}까지$offsets"
}
