package com.dearby.nativeapp.pages.recommendation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.widgets.recommendation.recommendationRows.RecommendationRow

/**
 * 추천 설정 퍼널 (contract "추천 설정 퍼널(발견 탭 첫 진입)"): one question per screen with a progress bar, `건너뛰기` (`나중에` on the intro) in the top bar
 * and a large filled button below. 분야 is upper fields and fields without one, then a sub-question per picked upper field (contract "추천 퍼널 분야 단계(상위 → 하위)");
 * all choices are FilterChips. No personal data is asked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun RecommendationFunnelPage(
    state: RecommendationFunnelState, toggle: (FunnelStep, String) -> Unit, next: () -> Unit, skip: () -> Unit, back: () -> Unit, retryTags: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = {}, navigationIcon = {
            if (state.step != FunnelStep.INTRO && state.step != FunnelStep.RESULT) IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
        }, actions = {
            if (state.step != FunnelStep.RESULT) TextButton(skip) { Text(if (state.step == FunnelStep.INTRO) "나중에" else "건너뛰기") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LinearProgressIndicator({ state.progress }, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (title, subtitle) = when (state.step) {
                FunnelStep.INTRO -> "나에게 맞는 활동을\n찾아 드릴게요" to "몇 가지 질문이면 끝나요.\n답하지 않은 건 그냥 넘어가도 돼요."
                FunnelStep.FIELDS -> "어떤 분야에\n관심 있어요?" to "여러 개 골라도 돼요."
                // One upper field asks about itself; 3 or more share one screen, each under its own question.
                FunnelStep.SUBFIELDS -> state.subScreens.getOrNull(state.subIndex)?.singleOrNull()?.let { "${it.option.label} 중에서는\n어떤 분야요?" to "여러 개 골라도 돼요." }
                    ?: ("분야별로 더\n골라 볼까요?" to "고르지 않으면 그 분야 전부를 찾아요.")
                FunnelStep.CATEGORIES -> "어떤 활동을\n찾고 있어요?" to "여러 개 골라도 돼요."
                FunnelStep.TRAITS -> "활동하면서\n무엇을 얻고 싶어요?" to "고르지 않아도 괜찮아요."
                // No answer from the recommendation API (not deployed yet, or offline): the settings were still saved.
                FunnelStep.RESULT -> state.found?.let { "지금 지원할 수 있는\n활동 ${it}개를 찾았어요" to "" } ?: ("설정을 저장했어요" to "추천이 준비되면 발견 맨 위에 보여 드릴게요.")
            }
            Text(title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            when (state.step) {
                FunnelStep.INTRO -> Unit
                FunnelStep.FIELDS, FunnelStep.TRAITS -> when {
                    state.tagsLoading -> CircularProgressIndicator()
                    state.tagsFailed -> TextButton(retryTags) { Text("목록을 불러오지 못했어요 · 다시 시도") }
                    (if (state.step == FunnelStep.FIELDS) state.fieldTree.isEmpty() else state.traitOptions.isEmpty()) ->
                        Text("아직 고를 항목이 없어요. 건너뛰어도 돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    state.step == FunnelStep.FIELDS -> Chips(null, state.fieldTree.map { it.option }, state.fields) { toggle(FunnelStep.FIELDS, it) }
                    else -> Chips(null, state.traitOptions, state.traits) { toggle(FunnelStep.TRAITS, it) }
                }
                // `{상위} 전부 좋아요` (the upper field's id) is picked while no child is.
                FunnelStep.SUBFIELDS -> state.subScreens.getOrNull(state.subIndex).orEmpty().let { nodes ->
                    nodes.forEach { node ->
                        val picked = state.subFields[node.option.id].orEmpty().ifEmpty { setOf(node.option.id) }
                        Chips(if (nodes.size > 1) "${node.option.label} 중에서는 어떤 분야요?" else null,
                            listOf(node.option.copy(label = "${node.option.label} 전부 좋아요")) + node.children, picked) { toggle(FunnelStep.SUBFIELDS, it) }
                    }
                }
                FunnelStep.CATEGORIES -> Chips(null, FUNNEL_CATEGORIES, state.categories) { toggle(FunnelStep.CATEGORIES, it) }
                FunnelStep.RESULT -> {
                    state.preview.forEach { RecommendationRow(it, null) }
                    if (state.eligibilityMissing) Text("지원 자격까지 맞추려면 프로필 › 지원 자격 정보를 입력해요", Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        DearbyButton(next, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp), enabled = !state.saving) {
            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            else Text(when (state.step) { FunnelStep.INTRO -> "시작하기"; FunnelStep.RESULT -> "추천 보러 가기"; else -> "다음" }, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 찾는 활동 종류: the contract's list mapped onto existing category codes only (멘토십 has no code and is left out, coordinator 2026-10-24). */
private val FUNNEL_CATEGORIES = listOf(ChipOption("BOOTCAMP", "부트캠프"), ChipOption("CLUB", "연합동아리"), ChipOption("CONTEST", "대회"),
    ChipOption("HACKATHON", "해커톤"), ChipOption("CONFERENCE", "컨퍼런스"), ChipOption("STUDY", "스터디"))

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Chips(title: String?, options: List<ChipOption>, picked: Set<String>, toggle: (String) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { Text(it, Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val selected = option.id in picked
                // 분야·성격 always show their tag icon (contract "아이콘+글자 칩"); 활동 종류 shows a check while picked.
                val icon = option.icon ?: Icons.Rounded.Check.takeIf { selected }
                FilterChip(selected, { toggle(option.id) }, { Text(option.label) },
                    leadingIcon = icon?.let { { Icon(it, null, Modifier.size(FilterChipDefaults.IconSize)) } })
            }
        }
    }
}
