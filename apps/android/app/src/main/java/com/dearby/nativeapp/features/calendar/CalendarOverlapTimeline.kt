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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dearby.nativeapp.shared.ui.Line
import com.dearby.nativeapp.shared.ui.Quiet
import com.dearby.nativeapp.shared.ui.Teal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The cropped activity block explicitly shows that the 17:00 end continues below the chart.
private val ContinuingBlock = GenericShape { size, _ ->
    moveTo(0f, 8f); quadraticTo(0f, 0f, 8f, 0f); lineTo(size.width - 8f, 0f)
    quadraticTo(size.width, 0f, size.width, 8f); lineTo(size.width, size.height - 8f)
    cubicTo(size.width * .84f, size.height - 24f, size.width * .68f, size.height + 10f, size.width * .48f, size.height - 3f)
    cubicTo(size.width * .22f, size.height - 18f, size.width * .16f, size.height + 6f, 0f, size.height - 8f)
    close()
}

@Composable internal fun CalendarOverlapTimeline(overlap: CalendarOverlapState) {
    val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of(overlap.activity.zone))
    fun time(value: Long) = clock.format(Instant.ofEpochMilli(value))
    val chartStart = overlap.activity.start
    val interval = 30 * 60_000L
    val rows = 4
    val rowHeight = 48.dp
    val gridHeight = rowHeight * rows
    val tail = 30.dp
    val busyTop = rowHeight * ((overlap.busy.start - chartStart).toFloat() / interval)
    val busyHeight = rowHeight * ((overlap.busy.end - overlap.busy.start).toFloat() / interval)
    Column(Modifier.clearAndSetSemantics {
        contentDescription = "30분 단위 시간표. 이 활동 ${time(overlap.activity.start)}부터 ${time(overlap.activity.end)}까지, 이후에도 계속. 예시 일정 ${time(overlap.busy.start)}부터 ${time(overlap.busy.end)}까지. 겹치는 구간 ${time(overlap.start)}부터 ${time(overlap.end)}까지."
    }) {
        Row(Modifier.padding(start = 40.dp, bottom = 12.dp)) {
            Text("이 활동", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("연결한 캘린더", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row {
            Box(Modifier.width(40.dp).height(gridHeight + tail)) {
                (0..rows).forEach { index -> Text(time(chartStart + interval * index), Modifier.offset(y = rowHeight * index - 6.dp), fontSize = 10.sp, color = Quiet) }
            }
            BoxWithConstraints(Modifier.weight(1f).height(gridHeight + tail)) {
                val column = maxWidth / 2
                Canvas(Modifier.fillMaxSize()) {
                    (0..rows).forEach { index ->
                        val y = (rowHeight * index).toPx()
                        drawLine(Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    listOf(8.dp, column).forEach { x -> drawLine(Line, Offset(x.toPx(), 0f), Offset(x.toPx(), size.height - 6.dp.toPx()), 1.dp.toPx()) }
                }
                Column(Modifier.offset(x = 10.dp).width(column - 20.dp).height(gridHeight + tail).clip(ContinuingBlock).background(Color(0xFFB4E0DC)).padding(9.dp)) {
                    Text(overlap.activity.title, color = Teal, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${time(overlap.activity.start)} – ${time(overlap.activity.end)}", color = Quiet, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text("이후에도 계속", Modifier.align(Alignment.CenterHorizontally), color = Teal, fontSize = 9.sp)
                    Text("⋮", Modifier.align(Alignment.CenterHorizontally), color = Teal, fontSize = 20.sp, lineHeight = 22.sp)
                }
                Box(Modifier.offset(y = busyTop).fillMaxWidth().height(busyHeight).background(Color(0xFFFFA726).copy(alpha = .18f)))
                Canvas(Modifier.fillMaxSize()) {
                    val effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    listOf(busyTop, busyTop + busyHeight).forEach { y ->
                        drawLine(Color(0xFFE99022), Offset(0f, y.toPx()), Offset(size.width, y.toPx()), 1.dp.toPx(), pathEffect = effect)
                    }
                }
                Box(Modifier.offset(y = busyTop).width(column).height(busyHeight), contentAlignment = Alignment.CenterEnd) {
                    Text("겹치는 구간", Modifier.padding(end = 12.dp), color = Color(0xFF9D5109), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.offset(x = column + 6.dp, y = busyTop).width(column - 14.dp).height(busyHeight).background(Color(0xFFBBD4ED), RoundedCornerShape(7.dp)).padding(9.dp)) {
                    Text("예시 일정", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${time(overlap.busy.start)} – ${time(overlap.busy.end)}", color = Quiet, fontSize = 10.sp)
                }
            }
        }
    }
}
