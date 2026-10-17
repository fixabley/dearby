package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactFields

/**
 * 내 프로필: data only. A profile never saved (`version` 0) starts with [ProfileSetupPage]; afterwards it is edited in place
 * and once [ProfileViewState.form] differs from the loaded profile the 저장/원래대로 bar appears (contract "프로필과 버전 이력").
 * Histories come from the step funnel; card decoration lives in 명함 만들기.
 */
@Composable fun ProfilePage(
    state: ProfileViewState, rows: List<HistoryRowState>, change: (ProfileFormState) -> Unit, addContact: (String) -> Unit, openHistory: (String?) -> Unit,
    removeHistory: (String) -> Unit, save: () -> Unit, revert: () -> Unit, versions: () -> Unit, compose: () -> Unit, signIn: () -> Unit, retry: () -> Unit,
    deleting: Boolean, deleteAccount: () -> Unit, changed: Boolean,
) {
    var confirming by remember { mutableStateOf(false) }
    // Never saved yet: the step funnel replaces the editor until the first save.
    val settingUp = state.profile?.version == 0
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("내 프로필") {
            if (state.phase == ProfilePhase.LOADED && !settingUp) {
                IconButton(versions) { Icon(Icons.Outlined.History, "변경 이력", tint = Teal) }
                TextButton(compose) { Text("명함 만들기") }
            }
        }
        when (state.phase) {
            ProfilePhase.SIGNED_OUT -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.", color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                DearbyButton(compose, Modifier.fillMaxWidth()) { Text("명함 만들기") }
                DearbyOutlineButton(signIn, Modifier.fillMaxWidth()) { Text("로그인") }
            }
            ProfilePhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
            ProfilePhase.FAILED -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("프로필을 불러오지 못했어요", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            ProfilePhase.LOADED -> state.form?.let { form ->
                if (settingUp) { ProfileSetupPage(form, state.saving, state.error, change, save, Modifier.weight(1f)); return@let }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("기본 정보", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Labeled("이름 · 필수") { DearbyInlineField("이름, 필수", form.name, { change(form.copy(name = it)) }, editing = true, placeholder = "실명 또는 활동명") }
                    state.errors?.name?.let { DearbyFieldError(it) }
                    Labeled("직함") { DearbyInlineField("직함", form.job, { change(form.copy(job = it)) }, editing = true, placeholder = "예: 서비스 기획 · 커뮤니티") }
                    Labeled("소개") { DearbyInlineField("소개", form.introduction, { change(form.copy(introduction = it)) }, editing = true, placeholder = "처음 만난 사람에게 건넬 한두 문장", singleLine = false) }
                    Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    ProfileContactFields(form.phone, form.email, form.extras, { change(form.copy(phone = it)) }, { change(form.copy(email = it)) },
                        { row -> change(form.copy(extras = form.extras.map { if (it.id == row.id) row else it })) },
                        { id -> change(form.copy(extras = form.extras.filter { it.id != id })) }, addContact,
                        phoneError = state.errors?.phone, emailError = state.errors?.email)
                    Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    if (form.histories.isEmpty()) Text("아직 이력이 없어요. 조직이나 활동부터 골라 단계별로 적어요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                    rows.forEach { row -> HistoryRow(row, { openHistory(row.id) }) { removeHistory(row.id) } }
                    DearbyOutlineButton({ openHistory(null) }, Modifier.fillMaxWidth()) { Text("이력 추가") }
                    HorizontalDivider()
                    TextButton({ confirming = true }, Modifier.align(Alignment.CenterHorizontally), enabled = !deleting,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        if (deleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("계정 삭제")
                    }
                }
                if (changed) {
                    HorizontalDivider(color = Line)
                    Column(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DearbyOutlineButton(revert, Modifier.weight(1f), enabled = !state.saving) { Text("원래대로") }
                            DearbyButton(save, Modifier.weight(1f), enabled = !state.saving) {
                                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("저장")
                            }
                        }
                    }
                }
            }
        }
        if (state.phase == ProfilePhase.FAILED) {
            TextButton({ confirming = true }, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp), enabled = !deleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("계정 삭제") }
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("계정을 삭제할까요?") },
        text = { Text("명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.") },
        confirmButton = { TextButton({ confirming = false; deleteAccount() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("삭제") } },
        dismissButton = { TextButton({ confirming = false }) { Text("취소") } })
}

/** One history: 조직 · 프로그램 · 활동, role, period and the approval / verification tags. Tap to edit or attach evidence. */
@Composable private fun HistoryRow(row: HistoryRowState, open: () -> Unit, remove: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "고치거나 인증자료 올리기", onClick = open), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(row.detail, color = Quiet, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (row.verified) "확인됨" else "미확인", row.verified)
                    row.approvalTag?.let { Tag(it, false) }
                    if (!row.saved) Tag("저장 전", false)
                }
            }
            IconButton(remove) { Icon(Icons.Outlined.RemoveCircleOutline, "이력 삭제", tint = Quiet) }
        }
    }
}

@Composable private fun Tag(text: String, strong: Boolean) = Surface(color = if (strong) Mint else Soft, shape = RoundedCornerShape(999.dp)) {
    Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = if (strong) Teal else Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun Labeled(label: String, modifier: Modifier = Modifier, field: @Composable () -> Unit) = Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, color = Quiet, style = MaterialTheme.typography.labelMedium)
    field()
}
