package com.dearby.nativeapp.pages.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.activityCard.ActivityCard
import com.dearby.nativeapp.widgets.activity.progress.ProgressStage
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStatus
import com.dearby.nativeapp.widgets.activity.progress.StatusOption
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import com.dearby.nativeapp.widgets.activity.progress.stageIcon

/**
 * One applied activity's stages (contract "내 활동 진행 트랙"), compact (2026-10-26 사용자 "활동탐색 카드처럼 깔끔하게", "마진이 너무 크고 경계가
 * 뚜렷하지 않아"): the discovery [ActivityCard] as the head (tap → [openDetail]), then small headed sections, each one card of
 * divider-separated rows — `진행 단계` (with the track's badge, e.g. `D-3 · 면접`, beside the heading).
 * A stage row is icon, name and date, with the user's 대기/통과/탈락 on the right only on a 선발 단계 (contract "내 활동 서버 저장·일정 상태",
 * the user's own mark, never the organizer's result) and a timetable icon before it when the block has a 트랙·세션 시간표 (2026-10-26 사용자).
 * Tapping a row with actions unfolds them: `시간표 보기` ([openTimetable], the block's sheet) first, then the status choice, `겹치는 시간 확인`
 * ([checkCalendar]) and `캘린더에 추가`. 내 활동에서 빼기 stays a text button at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActivityProgressPage(
    progress: ProgressState, activity: ActivityState, back: () -> Unit, openDetail: () -> Unit,
    mark: (Int, StatusOption) -> Unit, checkCalendar: (Int) -> Unit, remove: () -> Unit = {}, addToCalendar: (CalendarDraft) -> Unit = {},
    openTimetable: (String) -> Unit = {},
) {
    var expanded by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("진행 단계", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityCard(activity.title, activity.status, activity.recruiting, activity.period, activity.date, activity.location, openDetail,
                fieldTag = activity.fieldTags.firstOrNull(), traitTags = activity.traitTags, deadlineCount = activity.deadlineCount, undated = activity.noRecruitment,
                subtitle = progress.subtitle)
            SectionHeading("진행 단계") { if (progress.badge.isNotEmpty()) ExampleBadge(progress.badge, accent = !progress.ended, danger = progress.failed) }
            SectionCard { progress.stages.forEachIndexed { index, stage ->
                if (index > 0) HorizontalDivider()
                StageRow(stage, expanded == index, { expanded = if (expanded == index) null else index }, { mark(index, it) }, { checkCalendar(index) }, addToCalendar, openTimetable)
            } }
            Text(PROGRESS_NOTE, Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            RemoveFromMineButton(remove, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable private fun SectionHeading(text: String, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        trailing()
    }

@Composable private fun SectionCard(content: @Composable ColumnScope.() -> Unit) =
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) { content() }

/**
 * One stage as a ListItem-height row: kind icon (TealFill check once past, primary while current), name (bold while current) and date,
 * a timetable icon (`시간표 있음`) then the chosen status on the right of a 선발 단계 (failed in error colour). A row with actions has a
 * chevron and unfolds them on tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StageRow(stage: ProgressStage, open: Boolean, toggle: () -> Unit, mark: (StatusOption) -> Unit, checkCalendar: () -> Unit, addToCalendar: (CalendarDraft) -> Unit, openTimetable: (String) -> Unit) {
    val current = stage.status == ProgressStatus.CURRENT
    val past = stage.status == ProgressStatus.PAST
    val actions = stage.timetableId != null || stage.options.isNotEmpty() || stage.hasWindow || stage.draft != null
    val state = when { past -> "지남"; current -> "진행 중"; else -> "예정" }
    Column {
        ListItem(
            { Text(stage.name, fontWeight = if (current) FontWeight.Bold else null, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            (if (actions) Modifier.clickable(role = Role.Button, onClickLabel = if (open) "접기" else "펼치기", onClick = toggle) else Modifier).semantics { stateDescription = state },
            supportingContent = { Text(if (current) "진행 중 · ${stage.dateText}" else stage.dateText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = {
                if (past) Icon(Icons.Rounded.CheckCircle, null, tint = TealFill)
                else Icon(stageIcon(stage.kind), null, tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stage.timetableId != null) Icon(Icons.Rounded.ViewTimeline, "시간표 있음", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stage.options.isNotEmpty()) stage.selected?.let { ExampleBadge(it.name, accent = it.passed, danger = it.failed) }
                if (actions) Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(open && actions) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Segmented buttons up to four choices (Material 3 guidance); an admin list longer than that wraps as filter chips. A stage
                // that is not a 선발 단계 has no options (통과 여부는 선발 단계에만, 2026-10-26).
                if (stage.options.isNotEmpty() && stage.options.size <= 4) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    stage.options.forEachIndexed { index, option ->
                        SegmentedButton(stage.selected == option, { mark(option) }, SegmentedButtonDefaults.itemShape(index, stage.options.size)) {
                            Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else if (stage.options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stage.options.forEach { option -> FilterChip(stage.selected == option, { mark(option) }, { Text(option.name) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    stage.timetableId?.let { id ->
                        TextButton({ openTimetable(id) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.ViewTimeline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("시간표 보기") }
                    }
                    if (stage.hasWindow) TextButton(checkCalendar, contentPadding = PaddingValues(0.dp)) { Text("겹치는 시간 확인") }
                    stage.draft?.let { draft ->
                        TextButton({ addToCalendar(draft) }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("캘린더에 추가") }
                    }
                }
            }
        }
    }
}
