package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * First profile entry, Toss-style (Mobbin: Toss 송금 퍼널 — one question per screen, a big question headline, one focused
 * field, a bottom button). Shown in 내 프로필 until the profile is saved once; then the inline editor takes over.
 */
@Composable fun ProfileSetupPage(form: ProfileFormState, saving: Boolean, error: String?, change: (ProfileFormState) -> Unit, save: () -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf(SetupStep.NAME) }
    var tried by remember { mutableStateOf(false) }
    val steps = SetupStep.entries
    val index = steps.indexOf(step)
    val focus = remember { FocusRequester() }
    val errors = form.errors()
    val stepError = when (step) { SetupStep.NAME -> errors.name; SetupStep.PHONE -> errors.phone; SetupStep.EMAIL -> errors.email; else -> null }
    fun next() {
        tried = true
        if (stepError != null) return
        tried = false
        if (step == SetupStep.SUMMARY) save() else step = steps[index + 1]
    }
    LaunchedEffect(step) { if (step != SetupStep.SUMMARY) runCatching { focus.requestFocus() } }
    Column(modifier.fillMaxWidth()) {
        Text("프로필 만들기", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() }, color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator({ (index + 1f) / steps.size }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { contentDescription = "${index + 1} / ${steps.size} 단계" }, color = Teal)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                SetupStep.NAME -> {
                    Question("이름이 어떻게 되세요?")
                    DearbyInlineField("이름, 필수", form.name, { change(form.copy(name = it)) }, editing = true, placeholder = "실명 또는 활동명",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { next() }), focusRequester = focus)
                    Hint("명함과 공개 프로필에 이 이름으로 보여요.")
                }
                SetupStep.JOB -> {
                    Question("어떤 일을 하세요? (선택)")
                    DearbyInlineField("직함", form.job, { change(form.copy(job = it)) }, editing = true, placeholder = "예: 서비스 기획 · 커뮤니티",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { next() }), focusRequester = focus)
                    Hint("명함에 직함으로 보여요. 나중에 적어도 돼요.")
                }
                SetupStep.PHONE -> {
                    Question("연락받을 전화번호를 알려 주세요")
                    DearbyInlineField("전화번호, 필수", form.phone, { change(form.copy(phone = it)) }, editing = true, placeholder = "010-0000-0000",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { next() }), focusRequester = focus)
                    Hint("명함에 넣을지는 명함을 만들 때 골라요.")
                }
                SetupStep.EMAIL -> {
                    Question("이메일 주소는요?")
                    DearbyInlineField("이메일, 필수", form.email, { change(form.copy(email = it)) }, editing = true, placeholder = "name@example.com",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { next() }), focusRequester = focus)
                }
                SetupStep.INTRODUCTION -> {
                    Question("처음 만난 사람에게 한두 문장으로 소개해 주세요 (선택)")
                    DearbyInlineField("소개", form.introduction, { change(form.copy(introduction = it)) }, editing = true, placeholder = "예: 커뮤니티 운영과 행사 기획을 해요", singleLine = false, focusRequester = focus)
                }
                SetupStep.SUMMARY -> {
                    Question("이렇게 저장할까요?")
                    Column(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryRow("이름", form.name); SummaryRow("직함", form.job); SummaryRow("전화번호", form.phone); SummaryRow("이메일", form.email); SummaryRow("소개", form.introduction)
                    }
                    TextButton({ step = SetupStep.NAME }, contentPadding = PaddingValues(0.dp)) { Text("내용 고치기") }
                    Hint("활동 이력은 저장한 뒤 단계별로 추가할 수 있어요.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (tried) stepError?.let { DearbyFieldError(it) }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index > 0) DearbyOutlineButton({ tried = false; step = steps[index - 1] }, Modifier.weight(1f), enabled = !saving) { Text("이전") }
            DearbyButton(::next, Modifier.weight(1f), enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (step == SetupStep.SUMMARY) "프로필 저장" else "다음")
            }
        }
    }
}

@Composable private fun Question(text: String) = Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)

@Composable private fun Hint(text: String) = Text(text, color = Quiet, style = MaterialTheme.typography.bodySmall)

@Composable private fun SummaryRow(label: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
    Text(label, Modifier.width(64.dp), color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    Text(value.ifEmpty { "비워 둠" }, color = if (value.isEmpty()) Quiet else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
}
