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
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryRow
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.profile.profileFields.JobTagPicker
import com.dearby.nativeapp.widgets.profile.profileFields.PickerItem
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactFields

/**
 * 내 프로필 (contract "프로필 읽기/수정 모드·상단 바 메뉴", 2026-10-20): a profile never saved (`version` 0) starts with [ProfileSetupPage]. Afterwards the tab is
 * **read-only by default** — name, 직무 chips, 소개, contacts (left icon + value) and history rows like the card body — and the top-bar `수정` switches the same screen to
 * the editor (floating-label fields, 이력 추가, 연락처 추가) with the 저장/원래대로 bar below. `수정` becomes `완료`: no changes → back to reading, changes → 저장/버리기/취소
 * dialog; system back in edit mode does the same. A successful save returns to reading ([ProfileViewState.editing]). 변경 이력·명함 만들기 live in the top-bar `더 보기` menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfilePage(
    state: ProfileViewState, rows: List<HistoryRowState>, change: (ProfileFormState) -> Unit, searchJobs: (String) -> Unit, addContact: (String) -> Unit, openHistory: (String?) -> Unit,
    removeHistory: (String) -> Unit, save: () -> Unit, revert: () -> Unit, versions: () -> Unit, compose: () -> Unit, signIn: () -> Unit, retry: () -> Unit,
    changed: Boolean, edit: (Boolean) -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {},
    /** 프로필 `추천` 묶음 (contract "추천 설정 퍼널·지원 자격 정보·추천"), drawn in read mode; null signed out and when the feature is not there. */
    recommendation: (@Composable () -> Unit)? = null,
    /** `나중에` on the first-profile funnel ([ProfileSetupPage]). */
    leaveSetup: () -> Unit = {},
    /** 프로필 사진 (contract "프로필 사진"): opens the system photo picker, then the crop screen; [deletePhoto] after the confirmation below. */
    pickPhoto: () -> Unit = {}, deletePhoto: () -> Unit = {},
    /** A contact row on the read-mode card opens or copies it (contract "연락처 아이콘·바로가기"). */
    contact: (ContactState) -> Unit = {},
    /** 연락처 인증 (contract "연락처 인증"): GitHub 연결 (the route opens the Custom Tab) and the 이메일 인증번호 steps; state in [ProfileViewState.verify]. */
    linkGithub: () -> Unit = {}, requestEmailCode: () -> Unit = {}, changeEmailCode: (String) -> Unit = {}, verifyEmail: () -> Unit = {},
) {
    // 직무 label row → focuses the picker's search field (contract "입력 영역 터치"); 계정 삭제 lives in 설정 since 2026-10-20.
    val jobSearchFocus = remember { FocusRequester() }
    // Never saved yet: the step funnel replaces the editor until the first save.
    val settingUp = state.settingUp
    val loaded = state.phase == ProfilePhase.LOADED && !settingUp
    var asking by remember { mutableStateOf(false) }
    var confirmPhotoDelete by remember { mutableStateOf(false) }
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
                CenteredMessage(Icons.Rounded.AccountCircle, "프로필은 계정에 저장돼요", "로그인하면 프로필을 저장하고 고칠 수 있어요.", Modifier.weight(1f).fillMaxWidth(),
                    primary = MessageAction("로그인", signIn))
            }
            ProfilePhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(20.dp)) { SkeletonCardBody(); SkeletonRows(3) }
            ProfilePhase.FAILED -> DearbyErrorState("프로필을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", retry, Modifier.weight(1f).fillMaxWidth())
            ProfilePhase.LOADED -> state.form?.let { form ->
                if (settingUp) { ProfileSetupPage(form, state.saving, state.error, state.jobSearch, searchJobs, change, save, leaveSetup, Modifier.weight(1f)); return@let }
                if (!state.editing) { ReadOnlyProfile(form.cardState(state.profile?.photoUrl), form.histories.historyCards(), { edit(true) }, compose, contact, Modifier.weight(1f), recommendation); return@let }
                Column(Modifier.weight(1f).dismissKeyboardOnScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("기본 정보", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    PhotoRow(form.name, state.profile?.photoUrl, state.photoBusy, state.photoError, pickPhoto) { confirmPhotoDelete = true }
                    // Floating-label rows (contract "플로팅 라벨·필수 표시"·"입력 영역 터치"): the whole row focuses its field; Next moves down, Done closes the keyboard.
                    DearbyField("이름", form.name, { change(form.copy(name = it)) }, placeholder = "실명 또는 활동명", error = state.errors?.name, required = true, imeAction = ImeAction.Next)
                    // 소개 링크(contract "소개 링크·소개 글 폐지"): 노션·포트폴리오 주소 하나, 선택. 형식은 서버가 보고 400 문구가 아래 오류 줄에 나온다.
                    DearbyField("소개 링크", form.introLink, { change(form.copy(introLink = it)) }, placeholder = "notion.so/…", imeAction = ImeAction.Next, leadingIcon = Icons.Rounded.Link,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri))
                    Labeled("직무", Modifier.pointerInput(jobSearchFocus) { detectTapGestures { runCatching { jobSearchFocus.requestFocus() } } }) {
                        JobTagPicker(form.jobTags, state.jobSearch.query, searchJobs, state.jobSearch.items.map { PickerItem(it.id, it.name, it.parent?.name ?: it.nameEn) },
                            state.jobSearch.loading, state.jobSearch.failed, { change(form.withJob(it.id, it.name)) }, { change(form.withJob(null, null, it)) },
                            { change(form.withoutJob(it)) }, full = form.jobs.size >= ProfileFormState.MAX_JOBS, searchFocus = jobSearchFocus)
                    }
                    Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    ProfileContactFields(form.phone, form.email, form.extras, { change(form.copy(phone = it)) }, { change(form.copy(email = it)) },
                        { row -> change(form.copy(extras = form.extras.map { if (it.id == row.id) row else it })) },
                        { id -> change(form.copy(extras = form.extras.filter { it.id != id })) }, addContact,
                        phoneError = state.errors?.phone, emailError = state.errors?.email,
                        emailVerified = form.emailVerified, emailUnverifying = form.emailUnverifying, emailVerify = state.verify.emailAvailable && form.emailCanVerify,
                        emailBusy = state.verify.emailBusy, emailCode = state.verify.entry.takeIf { state.verify.challengeId != null },
                        onRequestEmailCode = requestEmailCode, onEmailCodeChange = changeEmailCode, onVerifyEmail = verifyEmail,
                        githubLink = state.verify.githubAvailable, githubBusy = state.verify.githubBusy, onLinkGithub = linkGithub)
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
    // Cards copy the photo when they are made (contract "프로필 사진": 발행 시점 복사), so they keep it.
    if (confirmPhotoDelete) AlertDialog({ confirmPhotoDelete = false }, title = { Text("프로필 사진을 지울까요?") },
        text = { Text("프로필 사진을 지워도 이미 만든 명함의 사진은 명함을 고치거나 지울 때까지 남아요.") },
        confirmButton = { TextButton({ confirmPhotoDelete = false; deletePhoto() }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmPhotoDelete = false }) { Text("취소") } })
}

/** The photo in edit mode: the round photo (or initial) with 사진 넣기/바꾸기 and 지우기. Optional; it saves at once, apart from 저장. */
@Composable private fun PhotoRow(name: String, photoUrl: String?, busy: Boolean, error: String?, pick: () -> Unit, delete: () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            PersonAvatar(name, photoUrl, 72.dp)
            if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        Column {
            TextButton(pick, enabled = !busy) { Text(if (photoUrl == null) "사진 넣기" else "사진 바꾸기") }
            if (photoUrl != null) TextButton(delete, enabled = !busy) { Text("사진 지우기", color = if (busy) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else MaterialTheme.colorScheme.error) }
        }
    }
    Text("선택이에요. 명함을 만들 때 넣을 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    error?.let { DearbyFieldError(it) }
}

/**
 * Read mode (contract "프로필 탭은 읽기 모드가 기본"; 2026-10-26 사용자 결정 "프로필 보기 모드 = 명함 디자인"): the card face on top (photo, name, 직무, contacts),
 * then the history rows as the card draws them ([CardHistoryRow], contract "내 프로필 활동 이력 = 명함 이력"), then `이 내용으로 명함 만들기` (primary) and `수정` (same as the top bar). No inputs, no bar.
 */
@Composable private fun ReadOnlyProfile(card: CardState, histories: List<CardHistoryState>, edit: () -> Unit, compose: () -> Unit, contact: (ContactState) -> Unit,
                                        modifier: Modifier = Modifier, recommendation: (@Composable () -> Unit)? = null) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CardContent(card, body = false, onContact = contact)
        Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (histories.isEmpty()) Text("활동 이력이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Column { histories.forEach { CardHistoryRow(it) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DearbyButton(compose, Modifier.fillMaxWidth()) { Text("이 내용으로 명함 만들기") }
            DearbyOutlineButton(edit, Modifier.fillMaxWidth()) { Text("수정") }
        }
        recommendation?.invoke()
    }
}

/** One history in edit mode: 조직 · 프로그램 · 활동, role, period and the approval / verification tags; the card opens the funnel and carries the remove button. */
@Composable private fun HistoryRow(row: HistoryRowState, open: () -> Unit, remove: () -> Unit) {
    // The whole card opens the history (contract "터치 컴포넌트": `Card(onClick)`); the trailing remove button keeps its own tap.
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)
    val shape = RoundedCornerShape(12.dp)
    OutlinedCard(open, Modifier.fillMaxWidth().semantics { onClick("고치거나 인증자료 올리기") { open(); true } }, border = border, shape = shape) {
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
