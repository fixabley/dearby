package com.dearby.nativeapp.pages.recommendation

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.DearbyFieldError
import com.dearby.nativeapp.shared.ui.DearbyTopBar

/**
 * 지원 자격 정보 동의 시트 (contract "지원 자격 정보(프로필, 동의 필요)"): the exact contract copy, shown inside a ModalBottomSheet the first time (and
 * again when the consent version moves on). [openTerms] shows the full text; closing is "not agreed".
 */
@Composable fun EligibilityConsentSheet(agree: () -> Unit, openTerms: () -> Unit, close: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("지원할 수 있는 활동만 보여 드리려면 이 정보가 필요해요", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("모으는 정보: 재학 상태, 학년, 태어난 해", "쓰는 곳: 지원 자격 거르기와 활동 추천", "보관: 내 계정에만, 다른 사람에게 보이지 않아요", "언제든 프로필에서 지우면 바로 삭제돼요")
                    .forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        ListItem(headlineContent = { Text("[필수] 지원 자격 정보 수집·이용 동의") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = openTerms),
            supportingContent = { Text("전문 보기") }, trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
        DearbyButton(agree, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("동의하고 입력하기", style = MaterialTheme.typography.titleMedium) }
        TextButton(close, Modifier.fillMaxWidth()) { Text("닫기") }
    }
}

/** An entered profile tapped again: 고치기 or `지원 자격 정보 지우기` (contract), as rows inside a ModalBottomSheet. */
@Composable fun EligibilityActionsSheet(summary: String, edit: () -> Unit, delete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(summary, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        ListItem(headlineContent = { Text("고치기") }, Modifier.clickable(role = Role.Button, onClick = edit), leadingContent = { Icon(Icons.Rounded.Edit, null) })
        ListItem(headlineContent = { Text("지원 자격 정보 지우기", color = MaterialTheme.colorScheme.error) }, Modifier.clickable(role = Role.Button, onClick = delete),
            leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) })
    }
}

/**
 * After consent, one question per screen (contract): 상황 → 학년 (재학·휴학·졸업 예정만) → 태어난 해 on the platform's NumberPicker wheel. Picking a 상황 or 학년
 * moves on at once; the last screen's button saves.
 */
@Composable fun EligibilityFormPage(
    state: EligibilityFormState, choose: (EducationChoice) -> Unit, chooseYear: (Int) -> Unit, birthYear: (Int) -> Unit, next: () -> Unit, back: () -> Unit,
) {
    val index = state.steps.indexOf(state.step).coerceAtLeast(0)
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("지원 자격 정보", back)
        LinearProgressIndicator({ (index + 1f) / state.steps.size }, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val title = when (state.step) { EligibilityStep.STATUS -> "지금 어떤 상황이에요?"; EligibilityStep.YEAR -> "몇 학년이에요?"; EligibilityStep.BIRTH -> "태어난 해를\n알려 주세요" }
            Text(title, Modifier.padding(bottom = 12.dp).semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            when (state.step) {
                EligibilityStep.STATUS -> Options(EducationChoice.entries.map { it.label }, EducationChoice.entries.indexOf(state.choice)) { choose(EducationChoice.entries[it]) }
                EligibilityStep.YEAR -> Options(SCHOOL_YEARS, (state.schoolYear ?: 0) - 1) { chooseYear(it + 1) }
                EligibilityStep.BIRTH -> {
                    // The platform wheel (android.widget.NumberPicker), the native counterpart of iOS's wheel picker.
                    AndroidView({ context -> NumberPicker(context).apply { wrapSelectorWheel = false; setOnValueChangedListener { _, _, value -> birthYear(value) } } },
                        Modifier.fillMaxWidth(), update = { picker ->
                            picker.minValue = state.years.first; picker.maxValue = state.years.last
                            picker.setFormatter { "${it}년" }; picker.value = state.birthYear
                        })
                    Text("나이 제한이 있는 활동을 거를 때만 써요. 내 계정에만 저장돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error?.let { DearbyFieldError(it) }
        }
        val last = state.step == EligibilityStep.BIRTH
        DearbyButton(next, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp),
            enabled = !state.saving && (last || (state.step == EligibilityStep.STATUS && state.choice != null) || (state.step == EligibilityStep.YEAR && state.schoolYear != null))) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            else Text(if (last) "완료" else "다음", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val SCHOOL_YEARS = listOf("1학년", "2학년", "3학년", "4학년", "5학년 이상")

/** Material radio rows (RadioButton + label as one selectable row). */
@Composable private fun Options(labels: List<String>, selected: Int, pick: (Int) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            ListItem(headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
                Modifier.clip(RoundedCornerShape(16.dp)).selectable(index == selected, onClick = { pick(index) }, role = Role.RadioButton),
                leadingContent = { RadioButton(index == selected, null) },
                colors = ListItemDefaults.colors(containerColor = if (index == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer))
        }
    }
}
