package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import com.dearby.nativeapp.features.contact.ContactActionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun CardContent(state: CardState, expanded: Boolean, onExpand: () -> Unit, modifier: Modifier = Modifier, onContact: (ContactActionState) -> Unit, showExpand: Boolean = true, titleBadge: Boolean = false) {
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Mint, contentColor = MaterialTheme.colorScheme.onSurface), border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFB6E4DF))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The identity stays outside the body scroll and keeps its position when details expand.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.person.ifBlank { "이름 없음" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text(state.job, Modifier.weight(1f), color = Quiet, style = MaterialTheme.typography.bodySmall)
                if (titleBadge) Surface(color = androidx.compose.ui.graphics.Color(0xFFD7F2EE), shape = MaterialTheme.shapes.small) { Text(state.title, Modifier.widthIn(max = 88.dp).padding(horizontal = 8.dp, vertical = 4.dp), color = Teal, style = MaterialTheme.typography.labelSmall) }
            }
            HorizontalDivider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.title, color = Teal, style = MaterialTheme.typography.titleLarge)
                if (state.description.isNotBlank()) Text(state.description, color = Quiet)
                if (expanded && state.introduction.isNotBlank()) Text(state.introduction)
                if (state.contacts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("연락처", style = MaterialTheme.typography.titleMedium, color = Quiet)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.contacts.forEach { contact ->
                            TextButton({ onContact(contact) }, Modifier.heightIn(min = 48.dp)) {
                                ContactSymbol(contact.kind, Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Column { Text(contact.label + if (contact.target == null) " · 복사" else ""); if (expanded) Text(contact.value, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                if (state.histories.isNotEmpty()) {
                    HorizontalDivider()
                    Text("활동 이력", style = MaterialTheme.typography.titleMedium, color = Quiet)
                    Column { state.histories.forEachIndexed { index, history -> TimelineEntry(if (expanded) "${history.startDate} – ${history.endDate ?: "현재"}" else historyMonth(history), history.title, if (expanded) listOf(history.role, history.description).filter { it.isNotBlank() }.joinToString("\n") else "", index == state.histories.lastIndex, compact = !expanded) } }
                }
            }
            if (showExpand) TextButton(onClick = onExpand) { Text(if (expanded) "접기" else "상세보기") }
        }
    }
}

/** Only real neighboring cards are shown behind the current card. No decorative fake identities. */
@Composable fun CardStack(cards: List<CardState>, index: Int, modifier: Modifier = Modifier, onContact: (ContactActionState) -> Unit, titleBadge: Boolean = false) {
    val headerStep = (40 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val behind = cards.drop(index + 1).take(2).reversed()
    Box(modifier.fillMaxWidth()) {
        behind.forEachIndexed { depth, card ->
            Surface(Modifier.padding(top = headerStep * depth, start = ((behind.size - depth) * 10).dp, end = ((behind.size - depth) * 10).dp).fillMaxWidth(), color = if (depth == 0) Soft else Mint, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(card.person, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1); Text(card.job, Modifier.weight(1f), color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 1); if (titleBadge) Text(card.title, Modifier.weight(1f), color = Teal, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
        CardContent(cards[index], false, {}, Modifier.padding(top = headerStep * behind.size).fillMaxWidth().heightIn(max = 460.dp), onContact, showExpand = false, titleBadge = titleBadge)
    }
}

private fun historyMonth(history: CardHistoryState): String {
    val start = history.startDate.take(7).replace('-', '.')
    val end = history.endDate?.take(7)?.replace('-', '.') ?: "현재"
    return if (start == end) start else "$start – $end"
}
