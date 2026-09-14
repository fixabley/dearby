package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
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
    var labelHeight by remember { mutableStateOf(80.dp * density.fontScale) }
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
                day.ticks.forEach { tick ->
                    Row(Modifier.offset(y = hour * (tick.minute / 60f)).fillMaxWidth().clearAndSetSemantics {},
                        verticalAlignment = Alignment.Top) {
                        Text(tick.label, Modifier.width(gutter).padding(end = Spacing.small),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = if (busy.isEmpty()) 1f else 0.72f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.offset(x = gutter, y = hour * (day.startMinute / 60f))
                        .width(width).height(maxOf(actualHeight, minimum))) {}
                busy.forEach { block ->
                    val top = hour * (block.startMinute / 60f)
                    val bottom = hour * (block.endMinute / 60f)
                    val activityTop = hour * (day.startMinute / 60f)
                    val protectedEnd = activityTop + labelHeight + Spacing.small
                    val labelTop = if (top < protectedEnd && bottom > activityTop) maxOf(top + Spacing.small, protectedEnd) else top + Spacing.small
                    val labelOffset = (labelTop - top).takeIf { labelTop + 64.dp * density.fontScale <= bottom }
                    TimelineBusyBlock(block, labelOffset, Modifier.offset(x = gutter, y = top)
                        .width(width).height(maxOf(1.dp, bottom - top)))

                }
                Box(Modifier.offset(x = gutter, y = hour * (day.startMinute / 60f)).width(width)
                    .testTag("timeline.block").clearAndSetSemantics {
                        contentDescription = "$title. ${day.description}" + (if (enlarged) ". 짧은 구간 확대 표시" else "") + if (hasOverlap) ". 기기 바쁜 시간과 겹침" else ""
                    }) {
                    Column(Modifier.testTag("activity.label").onSizeChanged { labelHeight = with(density) { it.height.toDp() } }.padding(Spacing.small)) {
                        Row(Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall), verticalAlignment = Alignment.CenterVertically) {
                            if (hasOverlap) Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(18.dp).testTag("activity.warning"), tint = MaterialTheme.colorScheme.onPrimary)
                            Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(day.timeText, Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                busy.forEach { block ->
                    if (block.overlapStartMinute != null && block.overlapEndMinute != null) {
                        TimelineIntersectionOutline(block.overlapDescription.orEmpty(),
                            Modifier.offset(x = gutter, y = hour * (block.overlapStartMinute / 60f)).width(width)
                                .height(hour * ((block.overlapEndMinute - block.overlapStartMinute) / 60f)))
                    }
                }
            }
        }
    }
    if (busy.isNotEmpty()) Text("활동과 내 일정이 겹치는 시간만 점선으로 표시합니다.", style = MaterialTheme.typography.labelSmall)
    if (enlarged) Text("짧은 구간은 블록을 확대해 표시합니다. 정확한 시간은 위 안내를 확인해 주세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
