package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.profile.profileFields.JobTagPicker
import com.dearby.nativeapp.widgets.profile.profileFields.PickerItem
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactFields

/**
 * 내 프로필: data only. A profile never saved (`version` 0) starts with [ProfileSetupPage]; afterwards it is edited in place
 * and once [ProfileViewState.form] differs from the loaded profile the 저장/원래대로 bar appears (contract "프로필과 버전 이력").
 * Histories come from the step funnel; card decoration lives in 명함 만들기. Inputs are Material 3 `OutlinedTextField`s at their default
 * size with the required/error sentence as supporting text (contract "폼은 플랫폼 기본 컴포넌트", 2026-10-19 밤).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun ProfilePage(
    state: ProfileViewState, rows: List<HistoryRowState>, change: (ProfileFormState) -> Unit, searchJobs: (String) -> Unit, addContact: (String) -> Unit, openHistory: (String?) -> Unit,
    removeHistory: (String) -> Unit, save: () -> Unit, revert: () -> Unit, versions: () -> Unit, compose: () -> Unit, signIn: () -> Unit, retry: () -> Unit,
    deleting: Boolean, deleteAccount: () -> Unit, changed: Boolean, settings: () -> Unit = {}, notify: () -> Unit = {},
) {
    var confirming by remember { mutableStateOf(false) }
    // Never saved yet: the step funnel replaces the editor until the first save.
    val settingUp = state.settingUp
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection).dismissKeyboardOnTap()) {
        // Pinned Material 3 top bar; the tab's own actions (변경 이력, 명함 만들기) sit left of the fixed 설정·알림 buttons (contract "설정").
        DearbyTopBar("내 프로필", scrollBehavior = topBar) {
            if (state.phase == ProfilePhase.LOADED && !settingUp) {
                IconButton(versions) { Icon(Icons.Rounded.History, "변경 이력", tint = MaterialTheme.colorScheme.primary) }
                TextButton(compose) { Text("명함 만들기") }
            }
            TabActions(settings, notify)
        }
        when (state.phase) {
            // Signed out and failed states are CenteredMessages (contract "비스크롤 화면 배치·CTA"); the 계정 삭제 text button stays under the failed state.
            ProfilePhase.SIGNED_OUT -> CenteredMessage(Icons.Rounded.AccountCircle, "프로필은 계정에 저장돼요", "명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.", Modifier.fillMaxSize(),
                primary = MessageAction("명함 만들기", compose), secondary = MessageAction("로그인", signIn))
            ProfilePhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            ProfilePhase.FAILED -> DearbyErrorState("프로필을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", retry, Modifier.weight(1f).fillMaxWidth())
            ProfilePhase.LOADED -> state.form?.let { form ->
                if (settingUp) { ProfileSetupPage(form, state.saving, state.error, state.jobSearch, searchJobs, change, save, Modifier.weight(1f)); return@let }
                Column(Modifier.weight(1f).imeNestedScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("기본 정보", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    // Stacked label + large input rows (contract "폼 라벨·입력 크기"·"입력 영역 터치"): the whole row focuses its field; Next moves down, Done closes the keyboard.
                    DearbyField("이름", form.name, { change(form.copy(name = it)) }, placeholder = "실명 또는 활동명", error = state.errors?.name, hint = "필수예요.", imeAction = ImeAction.Next)
                    Labeled("직무") {
                        JobTagPicker(form.jobTags, state.jobSearch.query, searchJobs, state.jobSearch.items.map { PickerItem(it.id, it.name, it.parent?.name ?: it.nameEn) },
                            state.jobSearch.loading, state.jobSearch.failed, { change(form.withJob(it.id, it.name)) }, { change(form.withJob(null, null, it)) },
                            { change(form.withoutJob(it)) }, full = form.jobs.size >= ProfileFormState.MAX_JOBS)
                    }
                    DearbyField("소개", form.introduction, { change(form.copy(introduction = it)) }, placeholder = "처음 만난 사람에게 건넬 한두 문장", minLines = 3)
                    Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    ProfileContactFields(form.phone, form.email, form.extras, { change(form.copy(phone = it)) }, { change(form.copy(email = it)) },
                        { row -> change(form.copy(extras = form.extras.map { if (it.id == row.id) row else it })) },
                        { id -> change(form.copy(extras = form.extras.filter { it.id != id })) }, addContact,
                        phoneError = state.errors?.phone, emailError = state.errors?.email)
                    Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    if (form.histories.isEmpty()) Text("아직 이력이 없어요. 조직이나 활동부터 골라 단계별로 적어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    rows.forEach { row -> HistoryRow(row, { openHistory(row.id) }) { removeHistory(row.id) } }
                    AddRow("이력 추가") { openHistory(null) }
                    HorizontalDivider()
                    TextButton({ confirming = true }, Modifier.align(Alignment.CenterHorizontally), enabled = !deleting,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        if (deleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("계정 삭제")
                    }
                }
                if (changed) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DearbyOutlineButton(revert, Modifier.weight(1f), enabled = !state.saving) { Text("원래대로") }
                            DearbyButton(save, Modifier.weight(1f), enabled = !state.saving) {
                                if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("저장")
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
    // The whole card opens the history (contract "터치 컴포넌트": `Card(onClick)`); the trailing remove button keeps its own tap.
    OutlinedCard(open, Modifier.fillMaxWidth().semantics { onClick("고치거나 인증자료 올리기") { open(); true } }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(row.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (row.verified) "확인됨" else "미확인", row.verified)
                    row.approvalTag?.let { Tag(it, false) }
                    if (!row.saved) Tag("저장 전", false)
                }
            }
            IconButton(remove) { Icon(Icons.Rounded.RemoveCircle, "이력 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun Tag(text: String, strong: Boolean) = Surface(color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(999.dp)) {
    Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun Labeled(label: String, modifier: Modifier = Modifier, field: @Composable () -> Unit) = Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    field()
}
