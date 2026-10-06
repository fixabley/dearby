package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun CardContent(state: CardState, modifier: Modifier = Modifier, onContact: (ContactState) -> Unit, expanded: Boolean = false, titleBadge: Boolean = false) {
    OutlinedCard(modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Mint), border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFB6E4DF))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.person, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (state.job.isNotBlank()) Text(state.job, Modifier.weight(1f), color = Quiet, style = MaterialTheme.typography.bodySmall)
                if (titleBadge && state.title.isNotBlank()) ExampleBadge(state.title, true)
            }
            HorizontalDivider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 프로필처럼 명함 이름·설명·소개가 없는 경우 빈 줄과 겹친 구분선을 그리지 않는다.
                val showIntroduction = expanded && state.introduction.isNotBlank()
                if (state.title.isNotBlank()) Text(state.title, color = Teal, style = MaterialTheme.typography.titleLarge)
                if (state.description.isNotBlank()) Text(state.description, color = Quiet, style = MaterialTheme.typography.bodyMedium)
                if (showIntroduction) Text(state.introduction, style = MaterialTheme.typography.bodyMedium)
                if (state.title.isNotBlank() || state.description.isNotBlank() || showIntroduction) HorizontalDivider()
                Text("연락처", style = MaterialTheme.typography.titleSmall, color = Quiet)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.contacts.forEach { contact -> TextButton({ onContact(contact) }, contentPadding = PaddingValues(0.dp)) {
                        ContactSymbol(contact.kind, Modifier.size(23.dp)); Spacer(Modifier.width(8.dp))
                        Column { Text(contact.label, color = Quiet, style = MaterialTheme.typography.bodySmall); if (expanded) Text(contact.value, style = MaterialTheme.typography.bodySmall) }
                    } }
                }
                HorizontalDivider()
                Text("활동 이력", style = MaterialTheme.typography.titleSmall, color = Quiet)
                Column { state.histories.forEachIndexed { index, history -> TimelineEntry(history.date, history.title, if (expanded) history.role else "", index == state.histories.lastIndex, compact = !expanded) } }
            }
        }
    }
}

@Composable fun CardStack(cards: List<CardState>, index: Int, modifier: Modifier = Modifier, onContact: (ContactState) -> Unit, titleBadge: Boolean = false) {
    val behind = cards.drop(index + 1).take(2).reversed()
    Box(modifier.fillMaxWidth()) {
        behind.forEachIndexed { depth, card ->
            Surface(Modifier.padding(top = (32 * depth).dp, start = ((behind.size - depth) * 10).dp, end = ((behind.size - depth) * 10).dp).fillMaxWidth(), color = if (depth == 0) Soft else Mint, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(card.person, style = MaterialTheme.typography.titleMedium)
                    Text(card.job, color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        CardContent(cards[index], Modifier.padding(top = (32 * behind.size).dp).fillMaxWidth().heightIn(max = 410.dp), onContact, titleBadge = titleBadge)
    }
}
