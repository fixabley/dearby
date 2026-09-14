package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import io.fixabley.dearby.shared.ui.theme.Spacing
import java.time.LocalDate

/** Read-only visualization. Selection emits a local date; no OS access or calendar mutation. */
@Composable
internal fun DayTimeline(interval: TimelineInterval, title: String, modifier: Modifier = Modifier, busy: BusyOverlayState? = null, onDate: (LocalDate) -> Unit = {}) {
    var selected by rememberSaveable(interval) { mutableLongStateOf(interval.firstDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(selected)
    val day = timelineDay(interval, date) ?: return
    LaunchedEffect(date, interval) { onDate(date) }
    val dayStart = date.atStartOfDay(interval.zone).toInstant()
    val window = BusyInterval(dayStart, date.plusDays(1).atStartOfDay(interval.zone).toInstant())
    val currentBusy = busy?.let {
        if (it.window == null || it.window == window) it
        else BusyOverlayState("선택 날짜의 바쁜 시간을 확인하는 중이에요.")
    }
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TimelineDateHeader(date, interval.adjacent(date, -1) != null, interval.adjacent(date, 1) != null,
            { interval.adjacent(date, -1)?.let { selected = it.toEpochDay() } },
            { interval.adjacent(date, 1)?.let { selected = it.toEpochDay() } }, { showPicker = true })
        Text(day.timeText, Modifier.clearAndSetSemantics { contentDescription = "$title. ${day.description}" },
            style = MaterialTheme.typography.bodyMedium)
        currentBusy?.let { Text(it.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall) }
        val blocks = mergedBusy(currentBusy?.intervals.orEmpty(), window).map {
            val start = it.start.atZone(interval.zone)
            val end = it.end.atZone(interval.zone)
            val time = "${koreanTime(start.toLocalTime())}–" +
                (if (it.end == window.end) "다음 날 오전 12시" else koreanTime(end.toLocalTime()))
            val offset = if (start.offset != end.offset) " (${start.offset} → ${end.offset})" else ""
            val intersection = timelineIntersection(it, BusyInterval(interval.start, interval.end))
            fun minute(value: java.time.Instant) = java.time.Duration.between(dayStart, value).toNanos() / 60_000_000_000f
            BusyPlotBlock(java.time.Duration.between(dayStart, it.start).toNanos() / 60_000_000_000f,
                java.time.Duration.between(dayStart, it.end).toNanos() / 60_000_000_000f,
                "바쁜 시간: $time$offset. $date, 시간대 ${interval.zone.id}, 종료 시각 제외", time + offset, intersection != null, intersection?.start?.let(::minute), intersection?.end?.let(::minute))
        }
        if (currentBusy != null) TimelineLegend()
        TimelinePlot(day, title, blocks)
        blocks.forEach { block -> TimelineBusySummary(block, block.overlaps) }
    }
    if (showPicker) TimelineDatePicker(interval, date, { selected = it.toEpochDay(); showPicker = false }, { showPicker = false })
}
