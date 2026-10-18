package com.dearby.nativeapp.widgets.activity.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.ExampleBadge
import com.dearby.nativeapp.shared.ui.Line
import com.dearby.nativeapp.shared.ui.Quiet
import com.dearby.nativeapp.shared.ui.Teal

private const val MAX_SEGMENTS = 4

/**
 * 내 활동 row (contract "내 활동 진행 트랙"): name, program · organization, the segment track (past filled Teal, current
 * hatched Teal outline, upcoming Line) with stage names under it, and the D-day badge. Over four stages the track shows
 * four and `+N`. Nothing on the track animates. The track is one accessibility node, [ProgressState.trackDescription].
 */
@Composable fun ActivityProgressRow(progress: ProgressState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(progress.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(progress.subtitle, color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ExampleBadge(progress.badge, accent = !progress.ended, danger = progress.failed)
            }
            StageTrack(progress)
        }
    }
}

@Composable private fun StageTrack(progress: ProgressState) {
    val shown = progress.stages.take(MAX_SEGMENTS)
    val more = progress.stages.size - shown.size
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = progress.trackDescription }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { Segment(it.status, Modifier.weight(1f)) }
            if (more > 0) Text("+$more", Modifier.width(28.dp), color = Quiet, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { stage ->
                val current = stage.status == ProgressStatus.CURRENT
                Text(stage.name, Modifier.weight(1f), color = if (current) Teal else Quiet, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (more > 0) Spacer(Modifier.width(28.dp))
        }
    }
}

@Composable private fun Segment(status: ProgressStatus, modifier: Modifier) {
    val shape = RoundedCornerShape(3.dp)
    val base = modifier.height(8.dp)
    when (status) {
        ProgressStatus.PAST -> Box(base.background(Teal, shape))
        ProgressStatus.UPCOMING -> Box(base.background(Line, shape))
        // Hatched: diagonal Teal lines inside a Teal outline.
        ProgressStatus.CURRENT -> Box(base.clip(shape).border(1.5.dp, Teal, shape).drawBehind {
            val step = 6.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(Teal, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                x += step
            }
        })
    }
}
