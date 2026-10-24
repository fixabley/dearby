package com.dearby.nativeapp.pages.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
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
 * 내 프로필 (contract "프로필 읽기/수정 모드·상단 바 메뉴", 2026-10-20): a profile never saved (`version` 0) starts with [ProfileSetupPage]. Afterwards the tab is
 * **read-only by default** — name, 직무 chips, 소개, contacts (left icon + value) and history rows like the card body — and the top-bar `수정` switches the same screen to
 * the editor (floating-label fields, 이력 추가, 연락처 추가) with the 저장/원래대로 bar below. `수정` becomes `완료`: no changes → back to reading, changes → 저장/버리기/취소
 * dialog; system back in edit mode does the same. A successful save returns to reading ([ProfileViewState.editing]). 변경 이력·명함 만들기 live in the top-bar `더 보기` menu.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun ProfilePage(
    state: ProfileViewState, rows: List<HistoryRowState>, change: (ProfileFormState) -> Unit, searchJobs: (String) -> Unit, addContact: (String) -> Unit, openHistory: (String?) -> Unit,
    removeHistory: (String) -> Unit, save: () -> Unit, revert: () -> Unit, versions: () -> Unit, compose: () -> Unit, signIn: () -> Unit, retry: () -> Unit,
    changed: Boolean, edit: (Boolean) -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {},
    /** 프로필 `추천` 묶음 (contract "추천 설정 퍼널·지원 자격 정보·추천"), drawn in read mode and while signed out; null when the feature is not there. */
    recommendation: (@Composable () -> Unit)? = null,
) {
    // 직무 label row → focuses the picker's search field (contract "입력 영역 터치"); 계정 삭제 lives in 설정 since 2026-10-20.
    val jobSearchFocus = remember { FocusRequester() }
    // Never saved yet: the step funnel replaces the editor until the first save.
    val settingUp = state.settingUp
    val loaded = state.phase == ProfilePhase.LOADED && !settingUp
    var asking by remember { mutableStateOf(false) }
    // 완료 and system back do the same thing in edit mode (lead 2026-10-20): ask when something changed, otherwise leave edit mode.
    val finish = { if (changed) asking = true else edit(false) }
    BackHandler(enabled = loaded && state.editing && !asking, onBack = finish)
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection).dismissKeyboardOnTap()) {
        // [수정/완료] [설정] [알림] [더 보기: 변경 이력, 명함 만들기] (contract "상단 바 구성(네 탭 공통)").
        DearbyTopBar("내 프로필", scrollBehavior = topBar) {
            if (loaded) {
                if (state.editing) TextButton(finish, enabled = !state.saving) { Text("완료") }
                else IconButton({ edit(true) }) { Icon(Icons.Rounded.Edit, "수정") }
            }
            TabActions(settings, notify, if (loaded) listOf(MenuAction("변경 이력", Icons.Rounded.History, versions), MenuAction("명함 만들기", Icons.Rounded.Badge, compose)) else emptyList())
        }
        when (state.phase) {
            // Signed out and failed states are CenteredMessages (contract "비스크롤 화면 배치·CTA").
            ProfilePhase.SIGNED_OUT -> {
                CenteredMessage(Icons.Rounded.AccountCircle, "프로필은 계정에 저장돼요", "명함을 만들거나 로그인하면 프로필을 저장하고 고칠 수 있어요.", Modifier.weight(1f).fillMaxWidth(),
                    primary = MessageAction("명함 만들기", compose), secondary = MessageAction("로그인", signIn))
                recommendation?.let { Box(Modifier.padding(20.dp)) { it() } }
            }
            ProfilePhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(20.dp)) { SkeletonCardBody(); SkeletonRows(3) }
            ProfilePhase.FAILED -> DearbyErrorState("프로필을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", retry, Modifier.weight(1f).fillMaxWidth())
            ProfilePhase.LOADED -> state.form?.let { form ->
                if (settingUp) { ProfileSetupPage(form, state.saving, state.error, state.jobSearch, searchJobs, change, save, Modifier.weight(1f)); return@let }
                if (!state.editing) { ReadOnlyProfile(form, rows, Modifier.weight(1f), recommendation); return@let }
                Column(Modifier.weight(1f).imeNestedScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("기본 정보", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    // Floating-label rows (contract "플로팅 라벨·필수 표시"·"입력 영역 터치"): the whole row focuses its field; Next moves down, Done closes the keyboard.
                    DearbyField("이름", form.name, { change(form.copy(name = it)) }, placeholder = "실명 또는 활동명", error = state.errors?.name, required = true, imeAction = ImeAction.Next)
                    Labeled("직무", Modifier.pointerInput(jobSearchFocus) { detectTapGestures { runCatching { jobSearchFocus.requestFocus() } } }) {
                        JobTagPicker(form.jobTags, state.jobSearch.query, searchJobs, state.jobSearch.items.map { PickerItem(it.id, it.name, it.parent?.name ?: it.nameEn) },
                            state.jobSearch.loading, state.jobSearch.failed, { change(form.withJob(it.id, it.name)) }, { change(form.withJob(null, null, it)) },
                            { change(form.withoutJob(it)) }, full = form.jobs.size >= ProfileFormState.MAX_JOBS, searchFocus = jobSearchFocus)
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
                }
                // The bar belongs to edit mode (contract "수정 모드"); its buttons wake up once something changed.
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DearbyOutlineButton(revert, Modifier.weight(1f), enabled = changed && !state.saving) { Text("원래대로") }
                        DearbyButton(save, Modifier.weight(1f), enabled = changed && !state.saving) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("저장")
                        }
                    }
                }
            }
        }
    }
    // 완료 with unsaved changes: 저장 / 버리기 / 취소 (contract "수정 모드").
    if (asking) AlertDialog({ asking = false }, confirmButton = { TextButton({ asking = false; save() }) { Text("저장") } },
        dismissButton = {
            TextButton({ asking = false; revert(); edit(false) }) { Text("버리기") }
            TextButton({ asking = false }) { Text("취소") }
        }, title = { Text("바꾼 내용을 저장할까요?") }, text = { Text("저장하지 않으면 바꾼 내용이 사라져요.") })
}

/** Read mode (contract "프로필 탭은 읽기 모드가 기본"): the same sections as the editor as read-only rows in the card body's idiom; no inputs, no bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReadOnlyProfile(form: ProfileFormState, rows: List<HistoryRowState>, modifier: Modifier = Modifier, recommendation: (@Composable () -> Unit)? = null) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(form.name.ifBlank { "이름 없음" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (form.jobs.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { form.jobs.forEach { ExampleBadge(it.title) } }
            // The introduction is typed text, not a filled template: plain text, no markdown.
            if (form.introduction.isNotBlank()) Text(form.introduction, style = MaterialTheme.typography.bodyLarge)
        }
        Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        val contacts = listOf("phone" to form.phone, "email" to form.email) + form.extras.map { it.kind to it.value }
        contacts.filter { it.second.isNotBlank() }.forEach { (kind, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(contactIcon(kind), contactKindName(kind), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) Text("활동 이력이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        rows.forEach { HistoryRow(it, null, null) }
        recommendation?.invoke()
    }
}

/** One history: 조직 · 프로그램 · 활동, role, period and the approval / verification tags. With [open] the card opens the funnel; with [remove] it carries the remove button; read mode passes neither. */
@Composable private fun HistoryRow(row: HistoryRowState, open: (() -> Unit)?, remove: (() -> Unit)?) {
    // The whole card opens the history (contract "터치 컴포넌트": `Card(onClick)`); the trailing remove button keeps its own tap.
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable ColumnScope.() -> Unit = {
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
            if (remove != null) IconButton(remove) { Icon(Icons.Rounded.RemoveCircle, "이력 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (open != null) OutlinedCard(open, Modifier.fillMaxWidth().semantics { onClick("고치거나 인증자료 올리기") { open(); true } }, border = border, shape = shape, content = content)
    else OutlinedCard(Modifier.fillMaxWidth(), border = border, shape = shape, content = content)
}

@Composable private fun Tag(text: String, strong: Boolean) = Surface(color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(999.dp)) {
    Text(text, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = if (strong) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun Labeled(label: String, modifier: Modifier = Modifier, field: @Composable () -> Unit) = Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    field()
}
