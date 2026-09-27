package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextDecoration
import com.dearby.nativeapp.shared.ui.*

@Composable fun CardEditor(profile: CardEditorState, busy: Boolean, publish: (PublishSelectionState) -> Unit, close: () -> Unit, hidden: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Empty is intentional: nothing is publicly selected without an explicit action.
    var contacts by remember { mutableStateOf(emptySet<String>()) }
    var histories by remember { mutableStateOf(emptySet<String>()) }
    FormColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(close) { Text("취소") }; Text("직접 만들기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); DearbyLogo(Modifier.width(64.dp)) }
        PersonHeader(profile.person, profile.job, profile.introduction)
        Field("명함 이름", name, { name = it }); Field("명함 설명", description, { description = it })
        Text("공개할 연락처", style = MaterialTheme.typography.titleLarge)
        Text("눌러서 공유할 연락처를 골라 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profile.contacts.forEach { contact ->
                val checked = contact.id in contacts
                Column(Modifier.widthIn(min = 64.dp, max = 120.dp).toggleable(checked, role = Role.Checkbox) { selected -> contacts = if (selected) contacts + contact.id else contacts - contact.id; if (!selected) hidden() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactSymbol(contact.kind, Modifier.size(32.dp).drawWithContent { drawContent(); if (!checked) drawLine(Quiet, Offset(0f, size.height), Offset(size.width, 0f), 2.dp.toPx()) }, tint = if (checked) Teal else Quiet)
                    Text(contact.label, color = if (checked) Teal else Quiet, style = MaterialTheme.typography.bodySmall, textDecoration = if (checked) TextDecoration.None else TextDecoration.LineThrough)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("활동 이력", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton({ contacts = profile.contacts.map { it.id }.toSet(); histories = profile.histories.map { it.id }.toSet() }) { Text("전체 선택") }
            TextButton({ contacts = emptySet(); histories = emptySet(); hidden() }) { Text("전체 해제") }
        }
        profile.histories.forEachIndexed { index, history ->
            Row(verticalAlignment = Alignment.Top) {
                TimelineEntry(history.date, history.label, history.detail, index == profile.histories.lastIndex, Modifier.weight(1f))
                Checkbox(history.id in histories, { checked -> histories = if (checked) histories + history.id else histories - history.id })
            }
        }
        Text("선택한 정보만 공개돼요. 게시한 명함은 프로필을 수정해도 바뀌지 않아요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        DearbyButton({ publish(PublishSelectionState(name, description, contacts, histories)) }, Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank()) { Text("선택한 정보로 명함 게시") }
    }
}
