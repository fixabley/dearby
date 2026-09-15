package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Body overlay. Labels use unoccupied vertical space; the adjacent summary is always complete. */
@Composable
internal fun TimelineBusyBlock(block: BusyPlotBlock, labelOffset: Dp?, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.68f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.testTag("busy.block").clearAndSetSemantics {
            contentDescription = block.description + if (block.overlaps) ". 활동과 겹침" else ". 활동과 겹치지 않음"
        }) {
        labelOffset?.let { offset ->
            Column(Modifier.padding(top = offset).testTag("busy.label").padding(horizontal = 8.dp)) {
                Text("바쁜 시간", color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(block.timeText, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
