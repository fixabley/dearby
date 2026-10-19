package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 사용자가 골라 추가한 연락처 한 줄의 값. */
data class ProfileExtraContact(val id: String, val kind: String, val value: String)

/** 연락처 추가 시트에서 고를 수 있는 종류와 이름·안내 문구. */
val ProfileContactKinds = listOf(
    Triple("kakao", "카카오톡", "오픈채팅 링크 또는 ID"), Triple("instagram", "Instagram", "@아이디"),
    Triple("github", "GitHub", "github.com/아이디"), Triple("behance", "Behance", "behance.net/아이디"),
)

/**
 * 프로필 연락처 입력. 전화번호·이메일은 필수 칸이고, 그 밖의 종류는 `연락처 추가`에서 골라 붙이고 지울 수 있다.
 * 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 보이고 바뀐 값을 알린다. 칸은 Material 3 `OutlinedTextField` 기본 크기이고 필수·오류 문장은
 * `supportingText`, 추가 연락처의 삭제는 칸의 `trailingIcon`, `연락처 추가`는 `DearbyOutlineButton`이다(contract "폼은 플랫폼 기본 컴포넌트", 2026-10-19 밤).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfileContactFields(
    phone: String, email: String, extras: List<ProfileExtraContact>,
    onPhoneChange: (String) -> Unit, onEmailChange: (String) -> Unit, onExtraChange: (ProfileExtraContact) -> Unit,
    onRemoveExtra: (String) -> Unit, onAdd: (String) -> Unit, modifier: Modifier = Modifier,
    phoneError: String? = null, emailError: String? = null,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(phone, onPhoneChange, Modifier.fillMaxWidth(), label = { Text("전화번호") }, placeholder = { Text("010-0000-0000") },
            isError = phoneError != null, supportingText = { Text(phoneError ?: "필수예요.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(email, onEmailChange, Modifier.fillMaxWidth(), label = { Text("이메일") }, placeholder = { Text("name@example.com") },
            isError = emailError != null, supportingText = { Text(emailError ?: "필수예요.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
        extras.forEach { item ->
            val meta = ProfileContactKinds.firstOrNull { it.first == item.kind }
            val label = meta?.second ?: item.kind
            OutlinedTextField(item.value, { onExtraChange(item.copy(value = it)) }, Modifier.fillMaxWidth(), label = { Text(label) },
                placeholder = { Text(meta?.third ?: "") }, singleLine = true,
                trailingIcon = { IconButton({ onRemoveExtra(item.id) }) { Icon(Icons.Outlined.RemoveCircleOutline, "$label 삭제") } })
        }
        DearbyOutlineButton({ choosing = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("연락처 추가")
        }
    }
    if (choosing) ModalBottomSheet({ choosing = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("추가할 연락처", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            ProfileContactKinds.forEach { (kind, label, hint) ->
                ListItem({ Text(label) }, Modifier.clickable(role = Role.Button) { choosing = false; onAdd(kind) }, supportingContent = { Text(hint) })
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

/** A read-only text field that opens the date picker; a transparent overlay takes the tap so the field never focuses, and the whole box is one button for accessibility. */
@Composable private fun DateField(label: String, required: Boolean, value: LocalDate?, pick: () -> Unit) {
    val text = value?.format(DateTimeFormatter.ofPattern("yyyy. M. d.")) ?: ""
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { role = Role.Button; contentDescription = "$label${if (required) ", 필수" else ""}, ${text.ifEmpty { "날짜 선택" }}"; onClick("$label 선택") { pick(); true } }) {
        OutlinedTextField(text, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) }, placeholder = { Text("날짜 선택") },
            supportingText = if (required) ({ Text("필수예요.") }) else null, trailingIcon = { Icon(Icons.Outlined.CalendarToday, null) }, singleLine = true)
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
