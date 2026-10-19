package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.profile.profileFields.ActivityInfoPicker
import com.dearby.nativeapp.widgets.profile.profileFields.EvidenceFields
import com.dearby.nativeapp.widgets.profile.profileFields.EvidenceRow
import com.dearby.nativeapp.widgets.profile.profileFields.HistoryPeriodFields
import com.dearby.nativeapp.widgets.profile.profileFields.PickerItem

/** Toss-style history entry: one question per screen with progress, then a summary with evidence (contract "이력"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun HistoryFunnelPage(
    state: FunnelState, change: (HistoryDraft) -> Unit, go: (HistoryDraft.Step) -> Unit, next: () -> Unit, searchQuery: (String) -> Unit,
    pickImage: () -> Unit, pickPdf: () -> Unit, deleteEvidence: (String) -> Unit, close: () -> Unit,
) {
    val draft = state.draft
    val steps = draft.steps
    val index = steps.indexOf(state.step).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().dismissKeyboardOnTap()) {
        DearbyTopBar(if (state.step == HistoryDraft.Step.SUMMARY) "이력 확인" else "이력 입력", close)
        LinearProgressIndicator({ (index + 1f) / steps.size }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { heading() }, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).imeNestedScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.step) {
                HistoryDraft.Step.KIND -> {
                    Text("어떤 이력인가요?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    Choice("조직 활동", "동아리·학회·회사 같은 조직에서 활동했어요", !draft.isActivity) { change(draft.copy(kind = "organization")) }
                    Choice("활동 참여", "컨퍼런스·해커톤·캠프 같은 활동에 참여했어요", draft.isActivity) { change(draft.copy(kind = "activity")) }
                }
                // 조직 이력은 조직만, 활동 이력은 활동만 묻는다; 직접 적은 활동만 소속 조직(필수)·프로그램(선택)을 이어서 묻는다
                HistoryDraft.Step.ORGANIZATION -> Picker(if (draft.isActivity) "어떤 조직에서 한 활동인가요?" else "어떤 조직인가요?", "조직 이름 검색", state, searchQuery, change)
                HistoryDraft.Step.PROGRAM -> Picker("어떤 프로그램인가요? (선택)", "프로그램 이름 검색", state, searchQuery, change)
                HistoryDraft.Step.ACTIVITY -> Picker("어떤 활동인가요?", "활동 이름 검색", state, searchQuery, change)
                HistoryDraft.Step.ROLE -> Picker("어떤 역할이었나요?", "역할 검색 (예: 운영진, 참가자)", state, searchQuery, change)
                HistoryDraft.Step.PERIOD -> {
                    Text("언제였나요?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    HistoryPeriodFields(draft.start, draft.end, draft.ongoing, { change(draft.copy(start = it)) }, { change(draft.copy(end = it)) }, { change(draft.copy(ongoing = it)) })
                }
                HistoryDraft.Step.DESCRIPTION -> {
                    Text("한 줄로 설명해 주세요 (선택)", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    DearbyField("설명", draft.description, { change(draft.copy(description = it)) }, placeholder = "맡은 일이나 성과", minLines = 3)
                }
                HistoryDraft.Step.SUMMARY -> Summary(state, pickImage, pickPdf, deleteEvidence) { go(HistoryDraft.Step.KIND) }
            }
            if (state.tried) draft.error(state.step)?.let { DearbyFieldError(it) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index > 0 && state.step != HistoryDraft.Step.SUMMARY) DearbyOutlineButton({ go(steps[index - 1]) }, Modifier.weight(1f)) { Text("이전") }
            DearbyButton(next, Modifier.weight(1f)) { Text(if (state.step == HistoryDraft.Step.SUMMARY) "완료" else "다음") }
        }
    }
}

@Composable private fun Picker(title: String, prompt: String, state: FunnelState, searchQuery: (String) -> Unit, change: (HistoryDraft) -> Unit) {
    val draft = state.draft
    val step = state.step
    // Rows show where an activity belongs (조직 · 프로그램); picking one carries those parents into the draft.
    ActivityInfoPicker(title, prompt, state.search.query, searchQuery,
        state.search.items.map { PickerItem(it.id, it.name, listOfNotNull(it.organization?.name, it.program?.name).joinToString(" · ").ifEmpty { it.nameEn }) },
        state.search.loading, state.search.failed, draft.pickedName(step), draft.typedName(step),
        { picked -> val item = state.search.items.firstOrNull { it.id == picked.id }; change(draft.picked(step, picked.id, picked.name, item?.organization, item?.program)) },
        { change(draft.cleared(step)) }, { change(draft.typed(step, it)) })
}

@Composable private fun Summary(state: FunnelState, pickImage: () -> Unit, pickPdf: () -> Unit, deleteEvidence: (String) -> Unit, edit: () -> Unit) {
    val draft = state.draft
    Text(draft.chainText, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    Text("${draft.roleText} · ${draft.summaryLine}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    if (draft.description.isNotBlank()) Text(draft.description, style = MaterialTheme.typography.bodyMedium)
    if (draft.hasCustomEntry) Text(
        if (draft.approval == "rejected") "거절됨" + (draft.approvalReason?.let { " · $it" } ?: "") else "직접 입력한 항목은 관리자 승인 뒤 명함에 넣을 수 있어요",
        color = if (draft.approval == "rejected") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    TextButton(edit, contentPadding = PaddingValues(0.dp)) { Text("내용 고치기") }
    HorizontalDivider()
    Text("인증자료", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    Text(if (draft.verification == "verified") "관리자가 확인했어요." else "건너뛰어도 돼요. 확인되기 전까지 ‘미확인’으로 보여요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    if (state.saved) EvidenceFields(draft.evidence.map { EvidenceRow(it.id, it.fileName, it.contentType == "application/pdf", it.size / 1024) }, state.evidenceBusy, state.evidenceError, pickImage, pickPdf, deleteEvidence)
    else Text("프로필을 저장한 뒤 이 이력을 열면 인증자료를 올릴 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun Choice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}
