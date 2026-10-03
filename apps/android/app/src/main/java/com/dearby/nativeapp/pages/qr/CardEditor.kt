package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.CardContent

@Composable fun CardEditor(profileCard: CardState, existing: CardState? = null, publish: (Set<String>, Set<String>, String) -> Unit, close: () -> Unit) {
    var contacts by remember { mutableStateOf((existing?.contacts ?: profileCard.contacts.filter { it.kind != "phone" && it.kind != "instagram" }).map { it.id }.toSet()) }
    var histories by remember { mutableStateOf((existing?.histories ?: profileCard.histories.take(2)).map { it.id }.toSet()) }
    var preset by remember { mutableStateOf(existing != null) }
    var name by remember { mutableStateOf(existing?.title ?: "네트워킹") }
    var preview by remember { mutableStateOf(false) }
    val draft = profileCard.copy(title = name, contacts = profileCard.contacts.filter { it.id in contacts }, histories = profileCard.histories.filter { it.id in histories })
    if (preview) Dialog({ preview = false }) {
        Surface { Column(Modifier.padding(12.dp)) { CardContent(draft, Modifier.heightIn(max = 540.dp), {}, expanded = true); TextButton({ preview = false }) { Text("미리보기 닫기") } } }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (existing == null) "직접 만들기" else "명함 편집", close) { DearbyLogo(Modifier.padding(end = 16.dp)) }
        FormColumn(Modifier.weight(1f)) {
            PersonHeader(profileCard.person, "", profileCard.introduction)
            Text("공개할 연락처", style = MaterialTheme.typography.titleLarge)
            Text("눌러서 공유할 연락처를 골라 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth()) {
                profileCard.contacts.filter { it.kind != "instagram" }.forEach { item ->
                    val selected = item.id in contacts
                    Column(Modifier.weight(1f).toggleable(selected, role = Role.Checkbox) { value -> contacts = if (value) contacts + item.id else contacts - item.id }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContactSymbol(item.kind, Modifier.size(27.dp).drawWithContent { drawContent(); if (!selected) drawLine(Quiet, Offset(0f, size.height), Offset(size.width, 0f), 2.dp.toPx()) }, if (selected) Teal else Quiet)
                        Text(if (item.kind == "phone") "전화" else item.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("활동 이력", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Checkbox(histories.size == profileCard.histories.size, { histories = if (it) profileCard.histories.map { item -> item.id }.toSet() else emptySet() }, Modifier.semantics { contentDescription = "활동 이력 전체 선택" })
                Text("전체 선택", color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
            profileCard.histories.forEachIndexed { index, item -> Row(verticalAlignment = Alignment.Top) {
                TimelineEntry(item.date, item.title, item.role, index == profileCard.histories.lastIndex, Modifier.weight(1f))
                Checkbox(item.id in histories, { selected -> histories = if (selected) histories + item.id else histories - item.id }, Modifier.semantics { contentDescription = "${item.title} 포함" })
            } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(preset, { preset = it }); Text("이 구성을 프리셋으로 저장", color = Quiet, style = MaterialTheme.typography.bodySmall) }
            if (preset) Field("명함 이름", name, { name = it })
            Text("예시 명함 · 선택한 내용은 이번 실행 중에만 유지돼요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DearbyButton({ publish(contacts, histories, name) }, Modifier.fillMaxWidth()) { Text(if (existing == null) "공유 카드 만들기" else "명함 수정 저장") }
            DearbyOutlineButton({ preview = true }, Modifier.fillMaxWidth()) { Text("미리보기") }
        }
    }
}
