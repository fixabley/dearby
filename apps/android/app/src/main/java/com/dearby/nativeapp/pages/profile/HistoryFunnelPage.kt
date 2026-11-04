package com.dearby.nativeapp.pages.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/**
 * Toss-style history entry (contract "이력 (History) — 역할 매칭", one funnel since 2026-11-03: 어디에서 → 역할 → 기간 → 설명): one question per screen
 * with progress, then a summary with evidence. The top-bar arrow and
 * system back go to the previous question with the answers kept (contract "뒤로 가기"); from the first question (a saved history: from its
 * summary) they close, asking first when something was changed. A saved history's first question goes back to its summary.
 */
@Composable fun HistoryFunnelPage(
    state: FunnelState, change: (HistoryDraft) -> Unit, go: (HistoryDraft.Step) -> Unit, next: () -> Unit, searchQuery: (String) -> Unit,
    pickImage: () -> Unit, pickPdf: () -> Unit, deleteEvidence: (String) -> Unit, close: () -> Unit,
) {
    val draft = state.draft
    val steps = draft.steps
    val index = steps.indexOf(state.step).coerceAtLeast(0)
    var discarding by remember { mutableStateOf(false) }
    val leave = { if (state.edited) discarding = true else close() }
    val back = {
        when {
            state.saved && state.step == HistoryDraft.Step.SUMMARY -> leave()
            state.saved && index == 0 -> go(HistoryDraft.Step.SUMMARY)
            index > 0 -> go(steps[index - 1])
            else -> leave()
        }
    }
    BackHandler(onBack = back)
    if (discarding) AlertDialog({ discarding = false }, title = { Text("작성 중인 내용을 버릴까요?") },
        confirmButton = { TextButton({ discarding = false; close() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ discarding = false }) { Text("취소") } })
    Column(Modifier.fillMaxSize().dismissKeyboardOnTap()) {
        DearbyTopBar(if (state.step == HistoryDraft.Step.SUMMARY) "이력 확인" else "이력 입력", back)
        LinearProgressIndicator({ (index + 1f) / steps.size }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { heading() }, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).dismissKeyboardOnScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.step) {
                // 어디에서: 조직·프로그램·활동 통합 검색(행에 종류·소속); 없으면 종류를 고르고 이름을 그대로 적는다 (contract "이력 (History) — 역할 매칭" 입력 퍼널)
                HistoryDraft.Step.UNIT -> ActivityInfoPicker("어디에서 활동했나요?", "조직·프로그램·활동 이름 검색", state.search.query, searchQuery,
                    state.search.items.map { PickerItem(it.id, it.name, listOfNotNull(it.organization?.name, it.program?.name).distinct().joinToString(" · ").ifEmpty { it.nameEn }, it.kind, unitLabel(it.kind)) },
                    state.search.loading, state.search.failed, draft.owner?.name, draft.ownerName,
                    { picked -> val hit = state.search.items.firstOrNull { it.kind == picked.kind && it.id == picked.id }; change(draft.pickedUnit(picked.kind ?: "organization", picked.id, picked.name, hit?.organization, hit?.program)) },
                    { change(draft.clearedUnit()) }, { change(draft.typedUnit(it)) },
                    belowTyped = { UnitKindChooser(draft.ownerKind) { change(draft.copy(ownerKind = it)) } })
                // 역할: 고른 단위의 역할 목록(열면 전부, 치면 좁힘), 없으면 직접 입력. 이름만 적은 단위는 역할 목록이 없으니 바로 적는다
                HistoryDraft.Step.ROLE -> if (draft.customUnit) {
                    Text("${draft.ownerName}에서 어떤 역할이었나요?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    DearbyField("역할", draft.roleName, { change(draft.typedRole(it)) }, placeholder = "예: 운영진, 참가자", hint = "직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요", required = true)
                } else ActivityInfoPicker("${draft.owner?.name}에서 어떤 역할이었나요?", "역할 검색 (예: 운영진, 참가자)", state.search.query, searchQuery,
                    state.search.items.map { PickerItem(it.id, it.name, it.nameEn) }, state.search.loading, state.search.failed, draft.role?.name, draft.roleName,
                    { change(draft.pickedRole(it.id, it.name)) }, { change(draft.clearedRole()) }, { change(draft.typedRole(it)) },
                    idleHint = "등록된 역할이 없어요. 역할을 적어 주세요.")
                HistoryDraft.Step.PERIOD -> {
                    Text("언제였나요?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    HistoryPeriodFields(draft.start, draft.end, draft.ongoing, { change(draft.copy(start = it)) }, { change(draft.copy(end = it)) }, { change(draft.copy(ongoing = it)) })
                }
                HistoryDraft.Step.DESCRIPTION -> {
                    Text("한 줄로 설명해 주세요 (선택)", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    DearbyField("설명", draft.description, { change(draft.copy(description = it)) }, placeholder = "맡은 일이나 성과", minLines = 3)
                }
                HistoryDraft.Step.SUMMARY -> Summary(state, pickImage, pickPdf, deleteEvidence) { go(HistoryDraft.Step.UNIT) }
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

/** 직접 적은 곳의 종류 (the server needs `ownerKind` for a typed unit): a Material 3 segmented row, 조직 by default. */
@Composable private fun UnitKindChooser(kind: String, onChange: (String) -> Unit) {
    val kinds = listOf("organization", "program", "activity")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        kinds.forEachIndexed { index, value ->
            SegmentedButton(kind == value, { onChange(value) }, SegmentedButtonDefaults.itemShape(index, kinds.size)) { Text(unitLabel(value)) }
        }
    }
}

@Composable private fun Summary(state: FunnelState, pickImage: () -> Unit, pickPdf: () -> Unit, deleteEvidence: (String) -> Unit, edit: () -> Unit) {
    val draft = state.draft
    Text(draft.title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    Text(listOf(draft.affiliation, draft.summaryLine).filter { it.isNotEmpty() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
