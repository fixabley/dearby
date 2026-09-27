package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.shared.ui.Field
import com.dearby.nativeapp.shared.ui.FormColumn
import java.util.UUID

@Composable fun ProfilePage(profile: ProfileState, busy: Boolean, loggedIn: Boolean, save: (ProfileState) -> Unit, login: () -> Unit, logout: () -> Unit, openImport: () -> Unit) {
    if (!loggedIn) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("내 프로필", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(100.dp))
            Icon(Icons.Outlined.Badge, null, Modifier.size(112.dp), tint = Teal)
            Spacer(Modifier.height(28.dp))
            Text("나를 소개하는 명함을 만들어보세요.", style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text("연락처와 활동 이력을 정리하고,\n상황에 맞는 명함으로 공유할 수 있어요.", color = Quiet, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            DearbyButton(login, Modifier.fillMaxWidth(), enabled = !busy) { Text("이메일로 로그인") }
            Spacer(Modifier.height(12.dp))
            Text("명함 받기와 기기 저장은 로그인 없이 이용할 수 있어요.", color = Quiet, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember(profile) { mutableStateOf(profile) }
    FormColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("내 프로필", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton({ editing = !editing }) { Text(if (editing) "편집 닫기" else "편집") }
        }
        if (!editing) {
            PersonHeader(profile.name.ifBlank { "이름을 입력해 주세요" }, profile.job, profile.introduction, filled = true)
            Surface(color = Mint, shape = MaterialTheme.shapes.small) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Teal); Text("저장한 정보는 명함에서 선택한 항목만 공개돼요.", color = Teal, style = MaterialTheme.typography.bodySmall) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("연락처", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ editing = true }) { Text("추가") } }
            profile.contacts.forEach { contact ->
                Surface(onClick = { editing = true }, color = Soft, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContactSymbol(contact.kind, Modifier.size(22.dp))
                        Text(contact.label.ifBlank { contactKindLabel(contact.kind) }, Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                        Text(contact.value, Modifier.weight(0.65f), style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Quiet)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("활동 이력", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ editing = true }) { Text("추가") } }
            Column { profile.histories.forEachIndexed { index, history -> TimelineEntry("${history.startDate} – ${history.endDate ?: "현재"}", history.title, listOf(history.role, history.description).filter { it.isNotBlank() }.joinToString("\n"), index == profile.histories.lastIndex) } }
            HorizontalDivider()
            TextButton(openImport) { Text("기기에 저장한 명함 가져오기") }
            TextButton(logout, enabled = !busy) { Text("로그아웃") }
        } else {
            Field("이름", draft.name, { draft = draft.copy(name = it) })
            Field("직무", draft.job, { draft = draft.copy(job = it) })
            Field("소개", draft.introduction, { draft = draft.copy(introduction = it) }, singleLine = false)
            Text("연락처", style = MaterialTheme.typography.titleLarge)
            draft.contacts.forEachIndexed { index, contact ->
                key(contact.id) {
                    var menu by remember { mutableStateOf(false) }
                    TextButton({ menu = true }) { Text("종류: ${contactKindLabel(contact.kind)}") }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("phone", "email", "kakao", "instagram", "github", "behance").forEach { kind -> DropdownMenuItem({ Text(contactKindLabel(kind)) }, { draft = draft.copy(contacts = draft.contacts.toMutableList().apply { set(index, contact.copy(kind = kind)) }); menu = false }) }
                    }
                    Field("표시 이름", contact.label, { value -> draft = draft.copy(contacts = draft.contacts.toMutableList().apply { set(index, contact.copy(label = value)) }) })
                    Field("연락처 값", contact.value, { value -> draft = draft.copy(contacts = draft.contacts.toMutableList().apply { set(index, contact.copy(value = value)) }) })
                    TextButton({ draft = draft.copy(contacts = draft.contacts.filterNot { it.id == contact.id }) }) { Text("연락처 삭제") }
                }
            }
            DearbyOutlineButton({ draft = draft.copy(contacts = draft.contacts + ContactState(UUID.randomUUID().toString(), "email", "", "")) }) { Text("연락처 추가") }
            Text("활동 이력", style = MaterialTheme.typography.titleLarge)
            draft.histories.forEachIndexed { index, history ->
                key(history.id) {
                    fun change(value: HistoryState) { draft = draft.copy(histories = draft.histories.toMutableList().apply { set(index, value) }) }
                    Field("활동 이름", history.title, { change(history.copy(title = it)) })
                    Field("역할", history.role, { change(history.copy(role = it)) })
                    Field("시작일 YYYY-MM-DD", history.startDate, { change(history.copy(startDate = it)) })
                    Field("종료일 (진행 중이면 비움)", history.endDate.orEmpty(), { change(history.copy(endDate = it.ifBlank { null })) })
                    Field("활동 설명", history.description, { change(history.copy(description = it)) }, singleLine = false)
                    TextButton({ draft = draft.copy(histories = draft.histories.filterNot { it.id == history.id }) }) { Text("활동 이력 삭제") }
                }
            }
            DearbyOutlineButton({ draft = draft.copy(histories = draft.histories + HistoryState(UUID.randomUUID().toString(), "", "", "")) }) { Text("활동 이력 추가") }
            DearbyButton({ save(draft) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("프로필 저장") }
        }
    }
}
