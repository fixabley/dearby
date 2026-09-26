package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import com.dearby.nativeapp.features.contact.ContactActionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun CardContent(state: CardState, expanded: Boolean, onExpand: () -> Unit, modifier: Modifier = Modifier, onContact: (ContactActionState) -> Unit) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header stays outside the expanding/scrolling body.
            Text(state.person.ifBlank { "이름 없음" }, style = MaterialTheme.typography.headlineSmall)
            Text(state.job, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text(state.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            if (!expanded) { Text(state.description, maxLines = 2); Text(state.introduction, maxLines = 2) }
            if (expanded) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.description)
                Text(state.introduction)
                state.contacts.forEach { contact ->
                    OutlinedButton({ onContact(contact) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        val icon = when (contact.kind) { "phone" -> Icons.Outlined.Phone; "email" -> Icons.Outlined.Email; else -> if (contact.target == null) Icons.Outlined.ContentCopy else Icons.Outlined.OpenInNew }
                        Icon(icon, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(contact.label + if (contact.target == null) " · 복사" else ""); Text(contact.value, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (state.histories.isNotEmpty()) Text("활동 이력", style = MaterialTheme.typography.titleMedium)
                state.histories.forEach { history ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("●", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${history.startDate} – ${history.endDate ?: "현재"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(history.title, style = MaterialTheme.typography.titleMedium)
                            Text(history.role, style = MaterialTheme.typography.bodyMedium)
                            if (history.description.isNotBlank()) Text(history.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            TextButton(onClick = onExpand) { Text(if (expanded) "접기" else "상세보기") }
        }
    }
}
