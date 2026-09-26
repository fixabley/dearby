package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.dearby.nativeapp.shared.ui.Field
import com.dearby.nativeapp.shared.ui.FormColumn

@Composable fun CardEditor(profile: CardEditorState, busy: Boolean, publish: (PublishSelectionState) -> Unit, close: () -> Unit, hidden: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Privacy-preserving initial selection: none. Selecting all is an explicit action.
    var contacts by remember { mutableStateOf(emptySet<String>()) }
    var histories by remember { mutableStateOf(emptySet<String>()) }
    FormColumn {
        TextButton(close) { Text("취소") }
        Text("새 명함", style = MaterialTheme.typography.headlineMedium)
        Text("게시한 명함은 현재 공개 선택의 스냅샷입니다.")
        Field("명함 이름", name, { name = it }); Field("명함 설명", description, { description = it })
        Row {
            TextButton({ contacts = profile.contacts.map { it.id }.toSet(); histories = profile.histories.map { it.id }.toSet() }) { Text("전체 선택") }
            TextButton({ contacts = emptySet(); histories = emptySet(); hidden() }) { Text("전체 해제") }
        }
        Text("공개 연락처", style = MaterialTheme.typography.titleLarge)
        profile.contacts.forEach { contact ->
            Row {
                Checkbox(contact.id in contacts, { checked -> contacts = if (checked) contacts + contact.id else contacts - contact.id; if (!checked) hidden() })
                Icon(if (contact.id in contacts) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (contact.id in contacts) "공개" else "숨김", tint = if (contact.id in contacts) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
                Text("${contact.label}", color = if (contact.id in contacts) MaterialTheme.colorScheme.onSurface else Color.Gray, textDecoration = if (contact.id in contacts) TextDecoration.None else TextDecoration.LineThrough)
            }
        }
        Text("공개 활동 이력", style = MaterialTheme.typography.titleLarge)
        profile.histories.forEach { history -> Row { Checkbox(history.id in histories, { checked -> histories = if (checked) histories + history.id else histories - history.id }); Text("${history.label}") } }
        Button({ publish(PublishSelectionState(name, description, contacts, histories)) }, enabled = !busy && name.isNotBlank()) { Text("선택한 정보로 명함 게시") }
    }
}
