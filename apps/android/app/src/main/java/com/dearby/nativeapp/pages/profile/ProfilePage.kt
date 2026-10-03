package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState

@Composable fun ProfilePage(profile: ProfileState, loggedIn: Boolean, save: (ProfileState) -> Unit, login: () -> Unit, logout: () -> Unit) {
    if (!loggedIn) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("내 프로필", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(80.dp))
            Icon(Icons.Outlined.Badge, null, Modifier.size(112.dp), tint = Teal)
            Spacer(Modifier.height(28.dp))
            Text("나를 소개하는 명함을 만들어보세요.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text("연락처와 활동 이력을 정리하고\n상황에 맞는 명함으로 공유할 수 있어요.", color = Quiet, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            DearbyButton(login, Modifier.fillMaxWidth()) { Text("로그인하고 시작하기") }
            Spacer(Modifier.height(12.dp))
            Text("계정 연결 없이 예시 프로필로 전환돼요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var draft by remember(profile) { mutableStateOf(profile) }
    FormColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("내 프로필", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton({ editing = !editing }) { Text(if (editing) "편집 닫기" else "편집") }
        }
        if (!editing) {
            PersonHeader(profile.name, profile.job, profile.introduction, filled = true)
            Surface(color = Mint, shape = MaterialTheme.shapes.small) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Teal)
                Text("명함에서 선택한 항목만 보여요. 변경은 이번 실행 중에만 유지돼요.", color = Teal, style = MaterialTheme.typography.bodySmall)
            } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("연락처", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ editing = true }) { Text("추가") } }
            profile.contacts.forEach { contact ->
                Surface(onClick = { editing = true }, color = Soft, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ContactSymbol(contact.kind, Modifier.size(22.dp))
                        Text(contact.label, Modifier.weight(.35f), style = MaterialTheme.typography.bodySmall)
                        Text(contact.value, Modifier.weight(.65f), style = MaterialTheme.typography.bodySmall)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Quiet)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("활동 이력", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ editing = true }) { Text("추가") } }
            Column { profile.histories.forEachIndexed { index, history -> TimelineEntry(history.date, history.title, history.role, index == profile.histories.lastIndex) } }
            TextButton(logout) { Text("비로그인 화면 보기") }
        } else {
            Field("이름", draft.name, { draft = draft.copy(name = it) })
            Field("직무", draft.job, { draft = draft.copy(job = it) })
            Field("소개", draft.introduction, { draft = draft.copy(introduction = it) }, singleLine = false)
            Text("연락처", style = MaterialTheme.typography.titleLarge)
            draft.contacts.forEach { contact -> Field(contact.label, contact.value, { value -> draft = draft.copy(contacts = draft.contacts.map { if (it.id == contact.id) it.copy(value = value) else it }) }) }
            DearbyOutlineButton({ draft = draft.copy(contacts = draft.contacts + ContactState("extra-${draft.contacts.size}", "link", "링크", "https://example.com")) }) { Text("연락처 추가") }
            Text("활동 이력", style = MaterialTheme.typography.titleLarge)
            draft.histories.forEach { history ->
                Field("활동 이름", history.title, { value -> draft = draft.copy(histories = draft.histories.map { if (it.id == history.id) it.copy(title = value) else it }) })
                Field("역할", history.role, { value -> draft = draft.copy(histories = draft.histories.map { if (it.id == history.id) it.copy(role = value) else it }) })
            }
            DearbyOutlineButton({ draft = draft.copy(histories = draft.histories + CardHistoryState("extra-${draft.histories.size}", "새로운 활동", "참여", "2026.10")) }) { Text("활동 이력 추가") }
            DearbyButton({ save(draft); editing = false }, Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank()) { Text("프로필 저장") }
        }
    }
}
