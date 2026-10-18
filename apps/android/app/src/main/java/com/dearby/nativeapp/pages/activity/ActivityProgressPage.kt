package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.progress.ProgressMark
import com.dearby.nativeapp.widgets.activity.progress.ProgressStage
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStatus
import com.dearby.nativeapp.widgets.activity.progress.stageIcon

/**
 * One applied activity's stages as a vertical timeline (contract "내 활동 진행 트랙"): kind icon, name, date or label,
 * status chip and the user's 통과/대기/탈락 choice per stage. The 참여 확정 switch of [activity] sits under the title since
 * the track row replaced the plain row. [openDetail] goes to the activity detail; [checkCalendar] opens the calendar
 * comparison on a stage with a timed window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActivityProgressPage(
    progress: ProgressState, activity: ActivityState, back: () -> Unit, openDetail: () -> Unit, confirm: (Boolean) -> Unit,
    mark: (Int, ProgressMark) -> Unit, checkCalendar: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("진행 단계", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(progress.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(progress.subtitle, Modifier.padding(top = 4.dp), color = Quiet, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp)) { ExampleBadge(progress.badge, accent = !progress.ended, danger = progress.failed) }
            ConfirmToggle(activity, confirm)
            DearbyOutlineButton(openDetail, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("활동 상세 보기") }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            progress.stages.forEachIndexed { index, stage ->
                StageItem(stage, last = index == progress.stages.lastIndex, { mark(index, it) }) { checkCalendar(index) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Quiet)
                Text("표시는 내가 남긴 것이며 주최 측 결과가 아니에요", color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StageItem(stage: ProgressStage, last: Boolean, mark: (ProgressMark) -> Unit, checkCalendar: () -> Unit) {
    val current = stage.status == ProgressStatus.CURRENT
    val past = stage.status == ProgressStatus.PAST
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp).background(if (past) Teal else Color.White, CircleShape).border(1.5.dp, if (past || current) Teal else Line, CircleShape), contentAlignment = Alignment.Center) {
                Icon(stageIcon(stage.kind), null, Modifier.size(20.dp), tint = if (past) Color.White else if (current) Teal else Quiet)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(if (past) Teal else Line))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stage.name, Modifier.weight(1f), fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                ExampleBadge(chipText(stage), accent = current, danger = stage.mark == ProgressMark.FAIL)
            }
            Text(stage.dateText, color = Quiet, style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProgressMark.entries.forEachIndexed { index, option ->
                    SegmentedButton(stage.mark == option, { mark(option) }, SegmentedButtonDefaults.itemShape(index, ProgressMark.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Mint, activeContentColor = Teal, activeBorderColor = Teal, inactiveBorderColor = Line)) {
                        Text(option.label)
                    }
                }
            }
            if (stage.hasWindow) TextButton(checkCalendar, contentPadding = PaddingValues(0.dp)) { Text("겹치는 시간 확인") }
        }
    }
}

private fun chipText(stage: ProgressStage) = when {
    stage.mark == ProgressMark.FAIL -> "탈락"
    stage.mark == ProgressMark.PASS -> "통과"
    stage.status == ProgressStatus.PAST -> "지남"
    stage.status == ProgressStatus.CURRENT -> "진행 중"
    else -> "예정"
}
