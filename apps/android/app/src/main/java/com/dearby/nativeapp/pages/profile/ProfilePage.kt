package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.dearby.nativeapp.shared.ui.Field
import com.dearby.nativeapp.shared.ui.FormColumn
import java.util.UUID

@Composable fun ProfilePage(profile: ProfileState, busy: Boolean, loggedIn: Boolean, save: (ProfileState) -> Unit, login: () -> Unit, logout: () -> Unit, openImport: () -> Unit) {
    if (!loggedIn) {
        FormColumn {
            Text("내 프로필", style = MaterialTheme.typography.headlineMedium)
            Text("로그인하고 나를 소개해 보세요", style = MaterialTheme.typography.titleLarge)
            Text("연락처와 활동 이력을 정리하고, 명함마다 공개할 정보를 직접 고를 수 있어요.")
            Button(login, enabled = !busy) { Text("이메일로 로그인") }
        }
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember(profile) { mutableStateOf(profile) }
    FormColumn {
        Text("내 프로필", style = MaterialTheme.typography.headlineMedium)
        Text(if (loggedIn) "전체 프로필 · 명함마다 공개 범위를 고를 수 있어요" else "기기 초안 · 명함 게시에는 로그인이 필요해요")
        Row { TextButton({ editing = !editing }) { Text(if (editing) "편집 닫기" else "편집") }; TextButton(if (loggedIn) logout else login, enabled = !busy) { Text(if (loggedIn) "로그아웃" else "이메일 로그인") } }
        if (loggedIn) TextButton(openImport) { Text("기기에 저장한 명함 가져오기") }
        if (!editing) {
            Text(profile.name.ifBlank { "이름을 입력해 주세요" }, style = MaterialTheme.typography.headlineSmall)
            Text(profile.job); Text(profile.introduction)
            Text("연락처", style = MaterialTheme.typography.titleLarge)
            profile.contacts.forEach { Text("${it.label.ifBlank { contactKindLabel(it.kind) }} · ${it.value}") }
            Text("활동 이력", style = MaterialTheme.typography.titleLarge)
            profile.histories.forEach { Text("${it.title} · ${it.role}\n${it.startDate} – ${it.endDate ?: "현재"}\n${it.description}") }
            TextButton({ editing = true }) { Text("연락처 / 활동 이력 추가") }
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
            OutlinedButton({ draft = draft.copy(contacts = draft.contacts + ContactState(UUID.randomUUID().toString(), "email", "", "")) }) { Text("연락처 추가") }
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
            OutlinedButton({ draft = draft.copy(histories = draft.histories + HistoryState(UUID.randomUUID().toString(), "", "", "")) }) { Text("활동 이력 추가") }
            Button({ save(draft) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("프로필 저장") }
        }
    }
}
