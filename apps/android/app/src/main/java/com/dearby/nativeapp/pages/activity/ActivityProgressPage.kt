package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.progress.ProgressStage
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStatus
import com.dearby.nativeapp.widgets.activity.progress.StatusOption
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import com.dearby.nativeapp.widgets.activity.progress.stageIcon

/**
 * One applied activity's stages as a vertical timeline (contract "내 활동 진행 트랙"): kind icon, name, date or label,
 * status chip and the user's choice among the stage's statuses (the schedule's admin list or 대기/통과/탈락; contract "내 활동 서버 저장·일정 상태"). The badge reads `참여 중` once every selection stage is passed.
 * [openDetail] goes to the activity detail; [checkCalendar] opens the calendar
 * comparison on a stage with a timed window. The bottom 내 활동에서 빼기 asks first, then [remove] removes it and leaves the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActivityProgressPage(
    progress: ProgressState, back: () -> Unit, openDetail: () -> Unit,
    mark: (Int, StatusOption) -> Unit, checkCalendar: (Int) -> Unit, remove: () -> Unit = {}, addToCalendar: (CalendarDraft) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("진행 단계", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(progress.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(progress.subtitle, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp)) { ExampleBadge(progress.badge, accent = !progress.ended, danger = progress.failed) }
            DearbyOutlineButton(openDetail, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("활동 상세 보기") }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            progress.stages.forEachIndexed { index, stage ->
                StageItem(stage, last = index == progress.stages.lastIndex, { mark(index, it) }, { checkCalendar(index) }, addToCalendar)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("표시는 내가 남긴 것이며 주최 측 결과가 아니에요", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            RemoveFromMineButton(remove, Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun StageItem(stage: ProgressStage, last: Boolean, mark: (StatusOption) -> Unit, checkCalendar: () -> Unit, addToCalendar: (CalendarDraft) -> Unit) {
    val current = stage.status == ProgressStatus.CURRENT
    val past = stage.status == ProgressStatus.PAST
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Past: TealFill circle with a white glyph (contract "청록 채움 면 위 글자는 흰색"); current: primary outline; upcoming: hairline.
            Box(Modifier.size(36.dp).background(if (past) TealFill else MaterialTheme.colorScheme.surface, CircleShape).border(1.5.dp, if (past) TealFill else if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(stageIcon(stage.kind), null, Modifier.size(20.dp), tint = if (past) Color.White else if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(if (past) TealFill else MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stage.name, Modifier.weight(1f), fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                ExampleBadge(chipText(stage), accent = current, danger = stage.selected?.failed == true)
            }
            Text(stage.dateText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            // Segmented buttons up to four choices (Material 3 guidance); an admin list longer than that wraps as filter chips. A stage
            // that is not a 선발 단계 has no options (통과 여부는 선발 단계에만, 2026-10-26) and shows only its date and 예정/지남.
            if (stage.options.isNotEmpty() && stage.options.size <= 4) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                stage.options.forEachIndexed { index, option ->
                    SegmentedButton(stage.selected == option, { mark(option) }, SegmentedButtonDefaults.itemShape(index, stage.options.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer, activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer, activeBorderColor = MaterialTheme.colorScheme.primary, inactiveBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else if (stage.options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stage.options.forEach { option -> FilterChip(stage.selected == option, { mark(option) }, { Text(option.name) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (stage.hasWindow) TextButton(checkCalendar, contentPadding = PaddingValues(0.dp)) { Text("겹치는 시간 확인") }
                stage.draft?.let { draft ->
                    TextButton({ addToCalendar(draft) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("캘린더에 추가") }
                }
            }
        }
    }
}

/** A passed or failed choice names itself (the admin's wording); otherwise the stage's place in time. */
private fun chipText(stage: ProgressStage) = stage.selected?.takeIf { it.failed || it.passed }?.name ?: when {
    stage.status == ProgressStatus.PAST -> "지남"
    stage.status == ProgressStatus.CURRENT -> "진행 중"
    else -> "예정"
}
