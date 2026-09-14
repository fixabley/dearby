package io.fixabley.dearby.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

/** Anonymous intervals behind the hour labels; the plot draws readable ticks above this layer. */
@Composable
internal fun TimelineBusyBand(block: BusyPlotBlock, overlaps: Boolean, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.testTag("busy.block").clearAndSetSemantics {
            contentDescription = block.description + if (overlaps) ". 활동과 겹침" else ". 활동과 겹치지 않음"
        }) {}
}
