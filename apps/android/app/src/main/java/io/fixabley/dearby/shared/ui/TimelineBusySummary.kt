package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.R
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun TimelineBusySummary(block: BusyPlotBlock, overlaps: Boolean) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = block.description + if (overlaps) ". 활동과 겹치는 시간" else ". 활동과 겹치지 않음"
    }, horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.Top) {
        if (overlaps) Icon(painterResource(R.drawable.ic_warning), null,
            Modifier.size(20.dp).testTag("busy.summary.warning"), tint = MaterialTheme.colorScheme.error)
        Text("바쁜 시간 · ${block.timeText}", style = MaterialTheme.typography.bodySmall)
    }
}
