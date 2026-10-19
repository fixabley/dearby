package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.profile.profileFields.JobTagPicker
import com.dearby.nativeapp.widgets.profile.profileFields.PickerItem

/**
 * First profile entry, Toss-style accordion (2026-10-17 user decision): sections stack in order, only one is open, and
 * answered sections collapse to a tall row that reopens on tap. Fields are Material 3 `OutlinedTextField`s at their default size
 * (contract "폼은 플랫폼 기본 컴포넌트", 2026-10-19 밤); the tab bar is hidden meanwhile (DearbyApp). Shown until the profile is saved once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileSetupPage(
    form: ProfileFormState, saving: Boolean, error: String?, jobSearch: PickerSearchState, searchJobs: (String) -> Unit,
    change: (ProfileFormState) -> Unit, save: () -> Unit, modifier: Modifier = Modifier,
) {
    val sections = SetupStep.entries
    var open by remember { mutableStateOf(SetupStep.NAME) }
    var reached by remember { mutableStateOf(setOf(SetupStep.NAME)) }
    var tried by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val errors = form.errors()
    fun errorOf(step: SetupStep) = when (step) { SetupStep.NAME -> errors.name; SetupStep.PHONE -> errors.phone; SetupStep.EMAIL -> errors.email; else -> null }
    val allReached = reached.size == sections.size
    fun next() {
        tried = true
        if (errorOf(open) != null) return
        tried = false
        val unreached = sections.firstOrNull { it !in reached }
        when {
            unreached != null -> { reached = reached + unreached; open = unreached }
            else -> save()
        }
    }
    fun reopen(step: SetupStep) { tried = false; open = step }
    LaunchedEffect(open) { if (open != SetupStep.JOBS) runCatching { focus.requestFocus() } }
    Column(modifier.fillMaxWidth()) {
        Text("프로필 만들기", Modifier.padding(horizontal = 24.dp, vertical = 12.dp).semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.weight(1f).imeNestedScroll().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sections.filter { it in reached }.forEach { step ->
                if (step == open) OpenSection(step, form, tried, errorOf(step), jobSearch, searchJobs, change, focus, ::next)
                else DoneRow(step, valueOf(step, form)) { reopen(step) }
            }
            if (allReached) Text("활동 이력은 저장한 뒤 단계별로 추가할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 12.dp)) {
            DearbyButton(::next, Modifier.fillMaxWidth(), enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                else Text(if (allReached) "프로필 저장" else "다음")
            }
        }
    }
}

private fun valueOf(step: SetupStep, form: ProfileFormState) = when (step) {
    SetupStep.NAME -> form.name; SetupStep.JOBS -> form.jobsText; SetupStep.PHONE -> form.phone; SetupStep.EMAIL -> form.email; SetupStep.INTRODUCTION -> form.introduction
}

/** The question for the open section with its one control: a default-size `OutlinedTextField` (label, supporting text, error) or the job picker. */
@Composable private fun OpenSection(
    step: SetupStep, form: ProfileFormState, tried: Boolean, error: String?, jobSearch: PickerSearchState, searchJobs: (String) -> Unit,
    change: (ProfileFormState) -> Unit, focus: FocusRequester, next: () -> Unit,
) {
    val nextAction = KeyboardActions(onNext = { next() })
    val shownError = if (tried) error else null
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (step) {
            SetupStep.NAME -> {
                Question("이름이 어떻게 되세요?")
                SetupField("이름", "이름, 필수", form.name, { change(form.copy(name = it)) }, "실명 또는 활동명", shownError, "필수예요. 명함과 공개 프로필에 이 이름으로 보여요.", focus,
                    KeyboardOptions(imeAction = ImeAction.Next), nextAction)
            }
            SetupStep.JOBS -> {
                Question("어떤 일을 하세요? (선택)")
                Hint("검색해서 여러 개 고를 수 있어요. 명함에 직무로 보여요.")
                JobTagPicker(form.jobTags, jobSearch.query, searchJobs, jobSearch.items.map { PickerItem(it.id, it.name, it.parent?.name ?: it.nameEn) }, jobSearch.loading, jobSearch.failed,
                    { change(form.withJob(it.id, it.name)) }, { change(form.withJob(null, null, it)) }, { change(form.withoutJob(it)) },
                    full = form.jobs.size >= ProfileFormState.MAX_JOBS)
            }
            SetupStep.PHONE -> {
                Question("연락받을 전화번호를 알려 주세요")
                SetupField("전화번호", "전화번호, 필수", form.phone, { change(form.copy(phone = it)) }, "010-0000-0000", shownError, "필수예요. 명함에 넣을지는 명함을 만들 때 골라요.", focus,
                    KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), nextAction)
            }
            SetupStep.EMAIL -> {
                Question("이메일 주소는요?")
                SetupField("이메일", "이메일, 필수", form.email, { change(form.copy(email = it)) }, "name@example.com", shownError, "필수예요.", focus,
                    KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), nextAction)
            }
            SetupStep.INTRODUCTION -> {
                Question("처음 만난 사람에게 한두 문장으로 소개해 주세요 (선택)")
                SetupField("소개", "소개", form.introduction, { change(form.copy(introduction = it)) }, "예: 커뮤니티 운영과 행사 기획을 해요", shownError, null, focus, minLines = 3)
            }
        }
    }
}

@Composable private fun SetupField(
    label: String, description: String, value: String, onValueChange: (String) -> Unit, placeholder: String, error: String?, hint: String?, focus: FocusRequester,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, minLines: Int = 1,
) = OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = description },
    label = { Text(label) }, placeholder = { Text(placeholder) }, isError = error != null,
    supportingText = (error ?: hint)?.let { { Text(it) } }, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    singleLine = minLines == 1, minLines = minLines)

/** An answered section: label and value on a row; tapping reopens it. */
@Composable private fun DoneRow(step: SetupStep, value: String, reopen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "${step.label} 고치기", onClick = reopen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(value.ifEmpty { "비워 둠" }, color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Question(text: String) = Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)

@Composable private fun Hint(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
