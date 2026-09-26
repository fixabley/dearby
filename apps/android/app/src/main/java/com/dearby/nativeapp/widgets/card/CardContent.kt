package com.dearby.nativeapp.widgets.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun CardContent(state: CardState, expanded: Boolean, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header stays outside the expanding/scrolling body.
            Text(state.person.ifBlank { "이름 없음" }, style = MaterialTheme.typography.headlineSmall)
            Text(state.job, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text(state.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            if (expanded) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.description)
                Text(state.introduction)
                state.contacts.forEach { Text(it) }
                state.histories.forEach { Text(it) }
            }
            TextButton(onClick = onExpand) { Text(if (expanded) "접기" else "상세보기") }
        }
    }
}
