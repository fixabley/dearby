package io.fixabley.dearby.shared.ui

import java.time.*

/** Half-open real interval, never a repeating daily time or inferred all-day event. */
internal data class TimelineInterval(val start: Instant, val end: Instant, val zone: ZoneId) {
    init { require(end > start) }
    val firstDate: LocalDate get() = start.atZone(zone).toLocalDate()
    val lastDate: LocalDate get() = end.minusNanos(1).atZone(zone).toLocalDate()
    fun contains(date: LocalDate): Boolean = date >= firstDate && date <= lastDate &&
        date.plusDays(1).atStartOfDay(zone).toInstant() > date.atStartOfDay(zone).toInstant()
    fun adjacent(date: LocalDate, direction: Long): LocalDate? {
        require(direction == 1L || direction == -1L)
        var next = date.plusDays(direction)
        while (next >= firstDate && next <= lastDate) {
            if (contains(next)) return next
            next = next.plusDays(direction) // Skip a nonexistent local calendar date, without a range array.
        }
        return null
    }
}
internal data class TimelineTick(val minute: Float, val label: String)
internal data class DayTimelineState(val date: LocalDate, val minutes: Float, val ticks: List<TimelineTick>,
    val startMinute: Float, val endMinute: Float, val timeText: String, val description: String)

internal fun timelineDay(interval: TimelineInterval, date: LocalDate): DayTimelineState? {
    if (!interval.contains(date)) return null
    val start = date.atStartOfDay(interval.zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(interval.zone).toInstant()
    fun minute(value: Instant) = (Duration.between(start, value).toNanos() / 60_000_000_000.0).toFloat()
    val clippedStart = maxOf(start, interval.start)
    val clippedEnd = minOf(end, interval.end)
    if (clippedEnd <= clippedStart) return null
    val hasOffsetChange = start.atZone(interval.zone).offset != end.minusNanos(1).atZone(interval.zone).offset
    val ticks = buildList {
        var tick = start
        while (tick < end) {
            val local = tick.atZone(interval.zone)
            add(TimelineTick(minute(tick), koreanTime(local.toLocalTime()) +
                if (hasOffsetChange) "\n${local.offset}" else ""))
            tick = tick.plusSeconds(3600)
        }
        add(TimelineTick(minute(end), "다음 날 0시"))
    }
    fun time(instant: Instant) = if (instant == end) "다음 날 오전 12시" else
        koreanTime(instant.atZone(interval.zone).toLocalTime())
    val text = "${time(clippedStart)}부터 ${time(clippedEnd)}까지"
    return DayTimelineState(date, minute(end), ticks, minute(clippedStart), minute(clippedEnd), text,
        "$date, $text, 시간대 ${interval.zone.id}. 정확한 구간: $clippedStart ~ $clippedEnd, 종료 시각 제외")
}
