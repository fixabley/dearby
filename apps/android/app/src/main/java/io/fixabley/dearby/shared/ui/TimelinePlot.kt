package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun TimelinePlot(day: DayTimelineState, title: String, busy: List<BusyPlotBlock> = emptyList()) {
    val density = LocalDensity.current
    val hour = 72.dp * density.fontScale
    val gutter = 84.dp // The hour label can wrap at large text instead of consuming the entire graph.
    val scroll = rememberScrollState()
    val actualHeight = hour * ((day.endMinute - day.startMinute) / 60f)
    val minimum = 64.dp * density.fontScale
    val enlarged = actualHeight < minimum
    LaunchedEffect(day.date) {
        scroll.scrollTo(with(density) { (hour * ((day.startMinute / 60f - 1f).coerceAtLeast(0f))).roundToPx() })
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.height(320.dp).fillMaxWidth().verticalScroll(scroll).testTag("timeline.scroll")) {
            val available = maxWidth - gutter - Spacing.small
            val hasOverlap = busy.any { it.overlaps }
            val width = available
            Box(Modifier.fillMaxWidth().height(hour * (day.minutes / 60f) + minimum)) {
                busy.forEach { block ->
                    TimelineBusyBand(block, block.overlaps,
                        Modifier.offset(y = hour * (block.startMinute / 60f)).width(gutter - Spacing.small)
                            .height(maxOf(1.dp, hour * ((block.endMinute - block.startMinute) / 60f))))
                }
                day.ticks.forEach { tick ->
                    Row(Modifier.offset(y = hour * (tick.minute / 60f)).fillMaxWidth().clearAndSetSemantics {},
                        verticalAlignment = Alignment.Top) {
                        Text(tick.label, Modifier.width(gutter).padding(end = Spacing.small),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.offset(x = gutter, y = hour * (day.startMinute / 60f))
                        .width(width).height(maxOf(actualHeight, minimum)).testTag("timeline.block")
                        .clearAndSetSemantics { contentDescription = "$title. ${day.description}" + (if (enlarged) ". 짧은 구간 확대 표시" else "") + if (hasOverlap) ". 기기 바쁜 시간과 겹침" else "" }) {
                    Column(Modifier.padding(Spacing.small)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasOverlap) Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(18.dp).testTag("activity.warning"))
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(day.timeText, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (busy.isNotEmpty()) Text("시간 눈금의 배경은 내 일정입니다. 겹치는 활동에는 경고 아이콘이 표시됩니다.", style = MaterialTheme.typography.labelSmall)
    if (enlarged) Text("짧은 구간은 블록을 확대해 표시합니다. 정확한 시간은 위 안내를 확인해 주세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
