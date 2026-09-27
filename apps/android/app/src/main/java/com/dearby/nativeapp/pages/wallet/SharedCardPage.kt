package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.features.contact.ContactActionState
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardState

@Composable fun SharedCardPage(card: CardState, busy: Boolean, back: () -> Unit, save: () -> Unit, send: () -> Unit, onContact: (ContactActionState) -> Unit) {
    var confirmSave by rememberSaveable { mutableStateOf(false) }
    if (confirmSave) AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = { confirmSave = false }, title = { Text("이 기기에 저장할까요?") }, text = { Text("이 기기에 명함을 저장해요. 로그인하지 않고 저장한 명함은 앱을 삭제하면 복구할 수 없어요.") }, confirmButton = { TextButton({ confirmSave = false; save() }, enabled = !busy) { Text("이 기기에 저장") } }, dismissButton = { TextButton({ confirmSave = false }) { Text("취소") } })
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(back) { Text("닫기") }; Text("공유 카드", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); DearbyLogo(Modifier.width(64.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PersonHeader(card.person, card.job, card.introduction)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                card.contacts.forEach { contact -> TextButton({ onContact(contact) }) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { ContactSymbol(contact.kind, Modifier.size(32.dp)); Text(contact.label) } } }
            }
            HorizontalDivider()
            Text("활동 이력", style = MaterialTheme.typography.headlineSmall)
            Column { card.histories.forEachIndexed { index, history -> TimelineEntry("${history.startDate} – ${history.endDate ?: "현재"}", history.title, listOf(history.role, history.description).filter { it.isNotBlank() }.joinToString("\n"), index == card.histories.lastIndex) } }
        }
        HorizontalDivider()
        Text("로그인 없이 카드를 저장할 수 있어요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
        DearbyButton({ confirmSave = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("카드 저장") }
        DearbyOutlineButton(send, Modifier.fillMaxWidth(), enabled = !busy) { Text("나도 카드 주기") }
    }
}
