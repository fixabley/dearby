package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun BusyCalendarControl(state: BusyDisplayState, onToggle: (Boolean) -> Unit, onSettings: () -> Unit, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("내 일정 표시", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(state.enabled, onCheckedChange = onToggle, enabled = !state.requesting,
                modifier = Modifier.testTag("busy.switch").semantics { contentDescription = "내 일정 표시" })
        }
        Text(state.message, style = MaterialTheme.typography.bodySmall)
        if (state.settings) TextButton(onClick = onSettings) { Text("앱 설정") }
        if (state.enabled) TextButton(onClick = onRetry) { Text("다시 조회") }
    }
}
