package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A block cut by the chart's bottom edge ends in a wave, so its end clearly continues below the chart.
private val ContinuingBlock = GenericShape { size, _ ->
    moveTo(0f, 8f); quadraticTo(0f, 0f, 8f, 0f); lineTo(size.width - 8f, 0f)
    quadraticTo(size.width, 0f, size.width, 8f); lineTo(size.width, size.height - 8f)
    cubicTo(size.width * .84f, size.height - 24f, size.width * .68f, size.height + 10f, size.width * .48f, size.height - 3f)
    cubicTo(size.width * .22f, size.height - 18f, size.width * .16f, size.height + 6f, 0f, size.height - 8f)
    close()
}

/**
 * One overlap on a timetable ([timelineAxis]): the activity on the left, the device event on the right, the shared stretch banded across
 * both. Ticks and blocks use one scale; blocks are clamped to the chart and say when they began earlier or go on later.
 */
@Composable internal fun CalendarOverlapTimeline(overlap: CalendarOverlapState) {
    val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of(overlap.activity.zone))
    fun time(value: Long) = clock.format(Instant.ofEpochMilli(value))
    val axis = timelineAxis(overlap)
    // One scale for ticks and blocks: about 0.8dp a minute, the whole chart kept between 192 and 288dp.
    val minutes = (axis.end - axis.start) / 60_000f
    val gridHeight = (0.8.dp * minutes).coerceIn(192.dp, 288.dp)
    fun y(value: Long) = gridHeight * ((value.coerceIn(axis.start, axis.end) - axis.start) / 60_000f / minutes)
    // A very short stretch still gets room for its label: grown downward, pushed up only at the chart's bottom.
    fun span(start: Long, end: Long, least: Dp): Pair<Dp, Dp> {
        val height = maxOf(y(end) - y(start), least).coerceAtMost(gridHeight)
        return minOf(y(start), gridHeight - height) to height
    }
    val busyTitle = overlap.busy.title ?: "제목 없는 일정"
    Column(Modifier.clearAndSetSemantics {
        contentDescription = "이 활동 ${time(overlap.activity.start)}부터 ${time(overlap.activity.end)}까지. 기기 일정 $busyTitle ${time(overlap.busy.start)}부터 ${time(overlap.busy.end)}까지. 겹치는 구간 ${time(overlap.start)}부터 ${time(overlap.end)}까지."
    }) {
        Row(Modifier.padding(start = 40.dp, bottom = 12.dp)) {
            Text("이 활동", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("연결한 캘린더", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row {
            Box(Modifier.width(40.dp).height(gridHeight)) {
                axis.ticks.forEach { tick -> Text(time(tick), Modifier.offset(y = y(tick) - 6.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            BoxWithConstraints(Modifier.weight(1f).height(gridHeight).clipToBounds()) {
                val column = maxWidth / 2
                val gridLine = MaterialTheme.colorScheme.surfaceContainerHighest
                Canvas(Modifier.fillMaxSize()) {
                    axis.ticks.forEach { tick ->
                        val y = y(tick).toPx()
                        drawLine(gridLine, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    listOf(8.dp, column).forEach { x -> drawLine(gridLine, Offset(x.toPx(), 0f), Offset(x.toPx(), size.height), 1.dp.toPx()) }
                }
                TimelineBlock(overlap.activity.title, overlap.activity.start, overlap.activity.end, axis, ::span, ::time,
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.offset(x = 10.dp).width(column - 20.dp))
                val (bandTop, bandHeight) = span(overlap.start, overlap.end, 18.dp)
                Box(Modifier.offset(y = bandTop).fillMaxWidth().height(bandHeight).background(Color(0xFFFFA726).copy(alpha = .18f)))
                Canvas(Modifier.fillMaxSize()) {
                    val effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    listOf(bandTop, bandTop + bandHeight).forEach { y ->
                        drawLine(Color(0xFFE99022), Offset(0f, y.toPx()), Offset(size.width, y.toPx()), 1.dp.toPx(), pathEffect = effect)
                    }
                }
                Box(Modifier.offset(y = bandTop).width(column).height(bandHeight), contentAlignment = Alignment.CenterEnd) {
                    Text("겹치는 구간", Modifier.padding(end = 12.dp), color = Color(0xFF9D5109), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                TimelineBlock(busyTitle, overlap.busy.start, overlap.busy.end, axis, ::span, ::time,
                    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.offset(x = column + 6.dp).width(column - 14.dp))
            }
        }
    }
}

/** A block from [start] to [end] clamped to [axis]: "이전부터 계속" when it began above the chart, a wave and "이후에도 계속" when it goes on below. */
@Composable private fun TimelineBlock(
    title: String, start: Long, end: Long, axis: TimelineAxisState, span: (Long, Long, Dp) -> Pair<Dp, Dp>, time: (Long) -> String, fill: Color, text: Color, modifier: Modifier,
) {
    val continues = end > axis.end
    val (top, height) = span(start, end, 44.dp)
    Column(modifier.offset(y = top).height(height).clip(if (continues) ContinuingBlock else RoundedCornerShape(7.dp)).background(fill).padding(9.dp)) {
        if (start < axis.start) Text("이전부터 계속", color = text, fontSize = 9.sp)
        Text(title, color = text, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${time(start)} – ${time(end)}", color = text, fontSize = 10.sp)
        if (continues) {
            Spacer(Modifier.weight(1f))
            Text("이후에도 계속", Modifier.align(Alignment.CenterHorizontally), color = text, fontSize = 9.sp)
        }
    }
}
