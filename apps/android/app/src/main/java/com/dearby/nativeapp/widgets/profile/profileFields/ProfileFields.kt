package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.input.KeyboardCapitalization

/** 연락처 추가 시트에서 고를 수 있는 종류와 이름·안내 문구. */
val ProfileContactKinds = listOf(
    Triple("kakao", "카카오톡", "카카오톡 ID"), Triple("instagram", "Instagram", "사용자 이름"),
    Triple("github", "GitHub", "사용자 이름"), Triple("behance", "Behance", "사용자 이름"),
)

/**
 * 프로필 연락처 입력. 전화번호·이메일은 필수 칸이고, 그 밖의 종류는 `연락처 추가`에서 골라 붙이고 지울 수 있다.
 * 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 보이고 바뀐 값을 알린다. 칸은 종류 아이콘(`contactIcon`)을 왼쪽에 둔 플로팅 라벨 `DearbyField`(라벨 = 종류 이름, 전화·이메일은
 * 빨간 `*` 필수, 포커스 시 형식 보기 placeholder; contract "연락처 행은 라벨 대신 왼쪽 아이콘"·"플로팅 라벨·필수 표시", 2026-10-20). 오류 문장은 칸 아래, 추가 연락처의 삭제는
 * 칸의 `trailingIcon`, `연락처 추가`는 행 전체가 눌리는 `AddRow`다(contract "입력 영역 터치").
 *
 * 연락처 인증(contract "연락처 인증", 2026-10-26): 인증된 값은 칸 끝에 [VerifiedMark], 그 값을 고치기 시작하면 칸 아래 "값을 바꾸면 인증이 풀려요". 이메일 칸 아래 `인증번호 받기`([emailVerify]가
 * true일 때)를 누르면 같은 자리에 6자리 숫자 칸(숫자 키패드, SMS 자동 입력 아님)과 `확인`·`다시 받기`(1분 뒤 활성, 남은 초 표시)가 온다([emailCode]가 열린 입력). GitHub 행 아래에는
 * `GitHub으로 연결`([githubLink]가 true일 때; Custom Tab은 화면이 연다). 503으로 기능이 꺼지면 화면이 두 플래그를 내려 버튼이 숨는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfileContactFields(
    phone: String, email: String, extras: List<ProfileExtraContact>,
    onPhoneChange: (String) -> Unit, onEmailChange: (String) -> Unit, onExtraChange: (ProfileExtraContact) -> Unit,
    onRemoveExtra: (String) -> Unit, onAdd: (String) -> Unit, modifier: Modifier = Modifier,
    phoneError: String? = null, emailError: String? = null,
    emailVerified: Boolean = false, emailUnverifying: Boolean = false,
    emailVerify: Boolean = false, emailBusy: Boolean = false, emailCode: EmailCodeEntry? = null,
    onRequestEmailCode: () -> Unit = {}, onEmailCodeChange: (String) -> Unit = {}, onVerifyEmail: () -> Unit = {},
    githubLink: Boolean = false, githubBusy: Boolean = false, onLinkGithub: () -> Unit = {},
) {
    var choosing by remember { mutableStateOf(false) }
    // A contact just added from the sheet takes the focus (and the keyboard) once its field is on screen.
    var focusAdded by remember { mutableStateOf(false) }
    val added = remember { FocusRequester() }
    LaunchedEffect(extras.size) { if (focusAdded) { focusAdded = false; runCatching { added.requestFocus() } } }
    // Icon-led large fields; Next walks down the fields and the last one answers Done (contract "키보드의 내리기 버튼").
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DearbyField("전화번호", phone, onPhoneChange, placeholder = "010-0000-0000", error = phoneError, required = true, imeAction = ImeAction.Next, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = contactIcon("phone"))
        DearbyField("이메일", email, onEmailChange, placeholder = "name@example.com", error = emailError, hint = UNVERIFY_HINT.takeIf { emailUnverifying }, required = true,
            imeAction = if (extras.isEmpty() && emailCode == null) ImeAction.Done else ImeAction.Next,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), leadingIcon = contactIcon("email"),
            trailingIcon = if (emailVerified) ({ VerifiedMark(Modifier.padding(end = 12.dp)) }) else null)
        if (emailVerify && !emailVerified) {
            if (emailCode == null) ActionLine { TextButton(onRequestEmailCode, enabled = !emailBusy) { Text("인증번호 받기") } }
            else EmailCodeFields(emailCode, emailBusy, onEmailCodeChange, onVerifyEmail, onRequestEmailCode)
        }
        extras.forEachIndexed { index, item ->
            val meta = ProfileContactKinds.firstOrNull { it.first == item.kind }
            val label = meta?.second ?: item.kind
            // Username kinds take the username only; a pasted URL is reduced to it (contract "연락처 사용자명 입력", 2026-10-26).
            DearbyField(label, item.value, { onExtraChange(item.typed(it)) }, placeholder = meta?.third, hint = UNVERIFY_HINT.takeIf { item.unverifying },
                imeAction = if (index == extras.lastIndex) ImeAction.Done else ImeAction.Next, prefix = item.prefix,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                focusRequester = added.takeIf { index == extras.lastIndex },
                leadingIcon = contactIcon(item.kind), trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.verified) VerifiedMark()
                        IconButton({ onRemoveExtra(item.id) }) { Icon(Icons.Rounded.RemoveCircle, "$label 삭제") }
                    }
                })
            // GitHub 연결: GitHub's sign-in proves the username; the server fills this row with the login (contract "연락처 인증").
            if (item.kind == "github" && githubLink && !item.verified) ActionLine {
                TextButton(onLinkGithub, enabled = !githubBusy) {
                    if (githubBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(contactIcon("github"), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub으로 연결")
                }
            }
        }
        AddRow("연락처 추가") { choosing = true }
    }
    if (choosing) ModalBottomSheet({ choosing = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("추가할 연락처", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ProfileContactKinds.forEach { (kind, label, hint) ->
                ListItem({ Text(label) }, Modifier.clickable(role = Role.Button) { choosing = false; focusAdded = true; onAdd(kind) }, supportingContent = { Text(hint) },
                    leadingContent = { Icon(contactIcon(kind), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        }
    }
}

private const val UNVERIFY_HINT = "값을 바꾸면 인증이 풀려요"

/** A right-aligned text button line under a field (`인증번호 받기`, `GitHub으로 연결`). */
@Composable private fun ActionLine(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = content)

/**
 * The 6-digit code field under the email: `NumberPassword` keypad (digits, no suggestions, no SMS fill — the code comes by mail), Done verifies, then
 * `다시 받기` (counting down to [EmailCodeEntry.resendAt]) and `확인` (enabled with 6 digits). The server's sentence for a wrong code sits under the field.
 */
@Composable private fun EmailCodeFields(entry: EmailCodeEntry, busy: Boolean, onCodeChange: (String) -> Unit, verify: () -> Unit, resend: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.resendAt) { while (System.currentTimeMillis() < entry.resendAt) { now = System.currentTimeMillis(); delay(1_000) }; now = System.currentTimeMillis() }
    val remaining = ((entry.resendAt - now) / 1_000).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DearbyField("인증번호", entry.code, onCodeChange, placeholder = "메일로 받은 6자리", error = entry.error, hint = "메일로 보낸 인증번호는 10분 동안 쓸 수 있어요.",
            imeAction = ImeAction.Done, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(onDone = { if (entry.complete && !busy) verify() }), leadingIcon = Icons.Rounded.Mail)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(resend, enabled = remaining == 0L && !busy) { Text(if (remaining > 0) "다시 받기 (${remaining}초)" else "다시 받기") }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(verify, enabled = entry.complete && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("확인")
            }
        }
    }
}

/**
 * 활동 이력의 기간 입력. 시작일(필수)·종료일은 읽기 전용 `OutlinedTextField`(달력 아이콘)를 눌러 `DatePickerDialog`로 고르고, `진행 중`은 `ListItem` 안의
 * `Switch`다. 고른 기간은 `2026.03 – 2026.06`으로 보인다. 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 맡는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HistoryPeriodFields(
    start: LocalDate?, end: LocalDate?, ongoing: Boolean,
    onStartChange: (LocalDate) -> Unit, onEndChange: (LocalDate?) -> Unit, onOngoingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, error: String? = null,
) {
    var picking by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(historyPeriod(start, if (ongoing) null else end, ongoing), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        DateField("시작일", required = true, value = start) { picking = "start" }
        // 글자를 눌러도 바뀌도록 줄 전체를 하나의 스위치로 만든다.
        ListItem({ Text("진행 중") }, Modifier.toggleable(ongoing, role = Role.Switch, onValueChange = onOngoingChange), trailingContent = { Switch(ongoing, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        if (!ongoing) DateField("종료일", required = false, value = end) { picking = "end" }
        error?.let { DearbyFieldError(it) }
    }
    picking?.let { which ->
        val initial = (if (which == "start") start else end ?: start)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        // 종료일은 시작일보다 앞선 날을 고를 수 없게 한다.
        val earliest = if (which == "end") start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() else null
        val state = rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = earliest == null || utcTimeMillis >= earliest
            override fun isSelectableYear(year: Int) = start == null || which != "end" || year >= start.year
        })
        DatePickerDialog({ picking = null }, confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (which == "start") onStartChange(date) else onEndChange(date)
                }
                picking = null
            }) { Text("확인") }
        }, dismissButton = { TextButton({ picking = null }) { Text("취소") } }) { DatePicker(state) }
    }
}

/** A read-only floating-label field that opens the date picker; a transparent overlay takes the tap so the field never focuses, and the whole box is one button for accessibility. */
@Composable private fun DateField(label: String, required: Boolean, value: LocalDate?, pick: () -> Unit) {
    val text = value?.format(DateTimeFormatter.ofPattern("yyyy. M. d.")) ?: ""
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { role = Role.Button; contentDescription = "$label${if (required) ", 필수" else ""}, ${text.ifEmpty { "날짜 선택" }}"; onClick("$label 선택") { pick(); true } }) {
        DearbyField(label, text, {}, required = required, readOnly = true, trailingIcon = { Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
        Box(Modifier.matchParentSize().clickable(onClick = pick))
    }
}

/** 연·월 위주 표시: `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`. */
fun historyPeriod(start: LocalDate?, end: LocalDate?, ongoing: Boolean): String {
    if (start == null) return "시작일을 골라 주세요"
    val month = DateTimeFormatter.ofPattern("yyyy.MM")
    val tail = if (ongoing) "진행 중" else end?.format(month) ?: "종료일 미정"
    return "${start.format(month)} – $tail"
}
