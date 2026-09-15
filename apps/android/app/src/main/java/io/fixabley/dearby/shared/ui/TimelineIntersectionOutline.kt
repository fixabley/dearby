package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

@Composable
internal fun TimelineIntersectionOutline(description: String, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.clipToBounds().testTag("timeline.intersection").clearAndSetSemantics {
        contentDescription = "실제 겹치는 구간. $description"
    }) {
        drawRect(color, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    }
}
