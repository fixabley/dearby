package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 * 값·형식 검사·오류 문구는 화면이 갖고 이 위젯은 보이고 바뀐 값을 알린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfileContactFields(
    phone: String, email: String, extras: List<ProfileExtraContact>,
    onPhoneChange: (String) -> Unit, onEmailChange: (String) -> Unit, onExtraChange: (ProfileExtraContact) -> Unit,
    onRemoveExtra: (String) -> Unit, onAdd: (String) -> Unit, modifier: Modifier = Modifier,
    phoneError: String? = null, emailError: String? = null,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Labeled("전화번호", required = true) {
            DearbyInlineField("전화번호, 필수", phone, onPhoneChange, editing = true, placeholder = "010-0000-0000", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            phoneError?.let { DearbyFieldError(it) }
        }
        Labeled("이메일", required = true) {
            DearbyInlineField("이메일, 필수", email, onEmailChange, editing = true, placeholder = "name@example.com", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
            emailError?.let { DearbyFieldError(it) }
        }
        extras.forEach { item ->
            val meta = ProfileContactKinds.firstOrNull { it.first == item.kind }
            val label = meta?.second ?: item.kind
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Labeled(label, required = false, modifier = Modifier.weight(1f)) {
                    DearbyInlineField(label, item.value, { onExtraChange(item.copy(value = it)) }, editing = true, placeholder = meta?.third ?: "")
                }
                IconButton({ onRemoveExtra(item.id) }) { Icon(Icons.Outlined.RemoveCircleOutline, "$label 삭제", tint = Quiet) }
            }
        }
        OutlinedButton({ choosing = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Line)) {
            Icon(Icons.Outlined.Add, null, tint = Teal); Spacer(Modifier.width(6.dp)); Text("연락처 추가", color = Teal, fontWeight = FontWeight.SemiBold)
        }
    }
    if (choosing) ModalBottomSheet({ choosing = false }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("추가할 연락처", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            ProfileContactKinds.forEach { (kind, label, _) ->
                Text(label, Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { choosing = false; onAdd(kind) }.padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * 활동 이력의 기간 입력. 시작일(필수)·종료일 선택기와 `진행 중` 스위치를 두고, 고른 기간을 `2026.03 – 2026.06`으로 보인다.
 * 날짜 검사(종료일이 시작일보다 앞서지 않음)와 오류 문구는 화면이 맡는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HistoryPeriodFields(
    start: LocalDate?, end: LocalDate?, ongoing: Boolean,
    onStartChange: (LocalDate) -> Unit, onEndChange: (LocalDate?) -> Unit, onOngoingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, error: String? = null,
) {
    var picking by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Text("기간", Modifier.weight(1f), color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            Text(historyPeriod(start, if (ongoing) null else end, ongoing), color = if (start == null) Quiet else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        DateRow("시작일", required = true, value = start) { picking = "start" }
        // 글자를 눌러도 바뀌도록 줄 전체를 하나의 스위치로 만든다.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(ongoing, role = Role.Switch, onValueChange = onOngoingChange), verticalAlignment = Alignment.CenterVertically) {
            Text("진행 중", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(ongoing, null)
        }
        if (!ongoing) DateRow("종료일", required = false, value = end) { picking = "end" }
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

@Composable private fun DateRow(label: String, required: Boolean, value: LocalDate?, pick: () -> Unit) {
    val text = value?.format(DateTimeFormatter.ofPattern("yyyy. M. d.")) ?: "날짜 선택"
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "$label 선택", onClick = pick)
        .clearAndSetSemantics { contentDescription = "$label${if (required) ", 필수" else ""}, $text" }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (required) Text(" 필수", color = Danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(text, color = if (value == null) Teal else MaterialTheme.colorScheme.onSurface, fontWeight = if (value == null) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun Labeled(label: String, required: Boolean, modifier: Modifier = Modifier, field: @Composable ColumnScope.() -> Unit) = Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        if (required) Text(" 필수", color = Danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
    field()
}

/** 연·월 위주 표시: `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`. */
fun historyPeriod(start: LocalDate?, end: LocalDate?, ongoing: Boolean): String {
    if (start == null) return "시작일을 골라 주세요"
    val month = DateTimeFormatter.ofPattern("yyyy.MM")
    val tail = if (ongoing) "진행 중" else end?.format(month) ?: "종료일 미정"
    return "${start.format(month)} – $tail"
}
