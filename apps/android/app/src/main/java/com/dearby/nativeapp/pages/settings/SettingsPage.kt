package com.dearby.nativeapp.pages.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 설정 (contract "설정", 2026-10-19 밤): a pushed screen with a back title and Material 3 `ListItem` sections — 계정 (가입일, 로그인 수단 수, 로그아웃,
 * 계정 삭제), 연결된 로그인 (Apple·Google: linked → email·date + 연결 해제, else 연결하기), 패스키 (rows open a menu: 이름 바꾸기·삭제; 패스키 추가 at the
 * bottom), 정보 (개인정보 처리방침·이용약관 in the in-app browser, app version). The last sign-in method cannot be removed: those buttons disable with
 * an explanation, and a server 409 reaches the screen as [SettingsState.notice]. Signed out ([SettingsPhase.SIGNED_OUT]) only the 로그인 CTA and 정보 show.
 * Confirmations (로그아웃·연결 해제·패스키 삭제·계정 삭제) are `AlertDialog`s; 이름 바꾸기 is a dialog with an `OutlinedTextField` (1~60자).
 */
@Composable fun SettingsPage(
    state: SettingsState, back: () -> Unit, signIn: () -> Unit, signOut: () -> Unit, deleteAccount: () -> Unit, retry: () -> Unit,
    link: (String) -> Unit, unlink: (String) -> Unit, addPasskey: () -> Unit, renamePasskey: (String, String) -> Unit, deletePasskey: (String) -> Unit,
    openPrivacy: () -> Unit, openTerms: () -> Unit, dismissNotice: () -> Unit, googleAvailable: Boolean = true,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var renaming by remember { mutableStateOf<SettingsPasskeyState?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("설정", back)
        when (state.phase) {
            SettingsPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            SettingsPhase.FAILED -> DearbyErrorState("설정을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
            SettingsPhase.SIGNED_OUT, SettingsPhase.LOADED -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                if (state.phase == SettingsPhase.SIGNED_OUT) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("로그인하면 계정·연결된 로그인·패스키를 관리할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        DearbyButton(signIn, Modifier.fillMaxWidth()) { Text("로그인") }
                    }
                } else {
                    SectionTitle("계정")
                    ValueRow("가입일", state.createdAt ?: "—")
                    ValueRow("로그인 수단", "${state.signInMethods}개 (패스키 ${state.passkeys.size}, 연결된 로그인 ${state.identities.count { it.linked }})")
                    ActionRow("로그아웃", enabled = !state.busy) { confirm = Confirm.SignOut }
                    ActionRow("계정 삭제", enabled = !state.busy, destructive = true, supporting = "명함·받은 명함 등 모든 정보가 지워져요.") { confirm = Confirm.DeleteAccount }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle("연결된 로그인")
                    state.identities.forEach { identity ->
                        if (identity.linked) ListItem({ Text(identity.label) },
                            supportingContent = { Text(listOfNotNull(identity.email, identity.linkedAt?.let { "$it 연결" }).joinToString(" · ") + if (state.lastMethod) "\n마지막 로그인 수단이라 해제할 수 없어요. 패스키나 다른 로그인을 먼저 추가해 주세요." else "") },
                            trailingContent = { TextButton({ confirm = Confirm.Unlink(identity) }, enabled = !state.busy && !state.lastMethod) { Text("연결 해제") } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        else {
                            // Google without a server/client id (contract "구글 로그인": the sign-in button hides) has no 연결하기 either.
                            val available = identity.key != "google" || googleAvailable
                            ListItem({ Text(identity.label) }, supportingContent = { Text(if (available) "연결되지 않음" else "지금은 ${identity.label} 로그인을 쓸 수 없어요.") },
                                trailingContent = if (available) ({ TextButton({ link(identity.key) }, enabled = !state.busy) { Text("연결하기") } }) else null,
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle("패스키")
                    if (state.passkeys.isEmpty()) Text("이 계정에 패스키가 없어요.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    state.passkeys.forEach { passkey ->
                        PasskeyRow(passkey, lastMethod = state.lastMethod, enabled = !state.busy, rename = { renaming = passkey }, delete = { confirm = Confirm.DeletePasskey(passkey) })
                    }
                    ActionRow("패스키 추가", enabled = !state.busy, supporting = "이 기기의 지문·얼굴·PIN으로 쓸 패스키를 더해요.", onClick = addPasskey)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                SectionTitle("정보")
                ActionRow("개인정보 처리방침", onClick = openPrivacy)
                ActionRow("이용약관", onClick = openTerms)
                ValueRow("앱 버전", state.version)
            }
        }
    }
    when (val current = confirm) {
        null -> Unit
        Confirm.SignOut -> ConfirmDialog("로그아웃할까요?", "이 기기에서 로그아웃해요. 패스키나 연결된 로그인으로 다시 로그인할 수 있어요.", "로그아웃", { confirm = null; signOut() }) { confirm = null }
        Confirm.DeleteAccount -> ConfirmDialog("계정을 삭제할까요?", "명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.", "삭제", { confirm = null; deleteAccount() }, destructive = true) { confirm = null }
        is Confirm.Unlink -> ConfirmDialog("${current.identity.label} 연결을 해제할까요?", "이 ${current.identity.label} 계정으로는 더 로그인할 수 없어요.", "연결 해제", { confirm = null; unlink(current.identity.key) }, destructive = true) { confirm = null }
        is Confirm.DeletePasskey -> ConfirmDialog("‘${current.passkey.label.ifBlank { "패스키" }}’를 삭제할까요?", "이 패스키로는 더 로그인할 수 없어요. 기기에 남은 패스키는 비밀번호 관리자에서 지울 수 있어요.", "삭제", { confirm = null; deletePasskey(current.passkey.id) }, destructive = true) { confirm = null }
    }
    renaming?.let { passkey -> RenameDialog(passkey.label, { renaming = null; renamePasskey(passkey.id, it) }) { renaming = null } }
    state.notice?.let { AlertDialog(onDismissRequest = dismissNotice, text = { Text(it) }, confirmButton = { TextButton(dismissNotice) { Text("확인") } }) }
}

private sealed interface Confirm {
    data object SignOut : Confirm
    data object DeleteAccount : Confirm
    data class Unlink(val identity: SettingsIdentityState) : Confirm
    data class DeletePasskey(val passkey: SettingsPasskeyState) : Confirm
}

@Composable private fun SectionTitle(text: String) =
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)

/** A read-only value row. */
@Composable private fun ValueRow(label: String, value: String) =
    ListItem({ Text(label) }, supportingContent = { Text(value) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))

/** A row whose whole surface is the action (contract "터치 영역": `ListItem` + `clickable`, 48dp+). */
@Composable private fun ActionRow(label: String, enabled: Boolean = true, destructive: Boolean = false, supporting: String? = null, onClick: () -> Unit) =
    ListItem({ Text(label, color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current) }, Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        supportingContent = supporting?.let { { Text(it) } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))

/** A passkey row: tapping it opens the 이름 바꾸기·삭제 menu anchored at the row; 삭제 is disabled for the last sign-in method. */
@Composable private fun PasskeyRow(passkey: SettingsPasskeyState, lastMethod: Boolean, enabled: Boolean, rename: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ListItem({ Text(passkey.label.ifBlank { "패스키" }) }, Modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = "이름 바꾸기 또는 삭제") { menu = true },
            supportingContent = { Text(listOfNotNull("${passkey.created} 만듦", passkey.lastUsed?.let { "$it 사용" } ?: "사용한 적 없음", if (passkey.backedUp) "기기 간 동기화됨" else "이 기기에만").joinToString(" · ")) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("이름 바꾸기") }, { menu = false; rename() })
            DropdownMenuItem({ Text("삭제", color = if (lastMethod) LocalContentColor.current else MaterialTheme.colorScheme.error) }, { menu = false; delete() }, enabled = !lastMethod)
            if (lastMethod) Text("마지막 로그인 수단이라 삭제할 수 없어요.", Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(max = 240.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, destructive: Boolean = false, onDismiss: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm, colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) { Text(action) } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } })

/** 이름 바꾸기: 1~60자 (contract "설정" PATCH `{label}`). */
@Composable private fun RenameDialog(current: String, save: (String) -> Unit, dismiss: () -> Unit) {
    var label by remember { mutableStateOf(current) }
    val valid = label.trim().length in 1..60
    AlertDialog(onDismissRequest = dismiss, title = { Text("패스키 이름") },
        text = { OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("이름") }, supportingText = { Text("1~60자") }, isError = !valid, singleLine = true) },
        confirmButton = { TextButton({ save(label.trim()) }, enabled = valid) { Text("저장") } }, dismissButton = { TextButton(dismiss) { Text("취소") } })
}
