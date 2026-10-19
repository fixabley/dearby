package com.dearby.nativeapp.pages.activity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.progress.ActivityProgressRow
import com.dearby.nativeapp.widgets.activity.progress.ProgressState

/**
 * Applied activities as progress tracks (contract "내 활동 진행 트랙"): `진행 중` first, `끝난 활동` (a 탈락 mark or every
 * stage over) collapsed below. An activity without stages keeps the plain row with the 참여 확정 switch. A track row opens
 * [openProgress]; a plain row opens the detail with [open]. The applied marks survive discovery searches (README rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MyActivitiesPage(state: ActivityListState, open: (String) -> Unit, openProgress: (String) -> Unit, confirm: (String, Boolean) -> Unit, explore: () -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {}) {
    val active = state.activeActivities
    val ended = state.endedActivities
    var showEnded by remember { mutableStateOf(false) }
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection)) {
        // Pinned Material 3 top bar (contract "상단 바 구성(네 탭 공통)", 2026-10-20): the 예시 badge is gone; the `더 보기` menu stays hidden because Android has no
        // example dataset for a `예시 데이터 보기/숨기기` item to toggle (내 활동 is the user's own applied marks).
        DearbyTopBar("내 활동", scrollBehavior = topBar) { TabActions(settings, notify) }
        // Empty: a CenteredMessage on the page background (contract "비스크롤 화면 배치·CTA"), the discovery CTA at the bottom.
        if (active.isEmpty() && ended.isEmpty()) DearbyEmptyState("신청한 활동이 없어요", "발견에서 관심 있는 활동을 신청해 보세요.", Modifier.fillMaxSize(), Icons.Rounded.Checklist, MessageAction("활동 둘러보기", explore))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(PROGRESS_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            if (active.isNotEmpty()) item { Text("진행 중", Modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
            items(active, key = { it.id }) { MyActivityRow(it, state.progress[it.id], open, openProgress, confirm) }
            if (ended.isNotEmpty()) {
                item { EndedHeader(ended.size, showEnded) { showEnded = !showEnded } }
                if (showEnded) items(ended, key = { it.id }) { MyActivityRow(it, state.progress[it.id], open, openProgress, confirm) }
            }
        }
    }
}

internal const val CONFIRM_NOTE = "참여 확정 표시는 내가 남기는 예시 표시예요. 주최 측 확정이 아니에요."
internal const val PROGRESS_NOTE = "단계와 참여 확정 표시는 내가 남기는 것이며 주최 측 결과가 아니에요."

@Composable private fun MyActivityRow(activity: ActivityState, progress: ProgressState?, open: (String) -> Unit, openProgress: (String) -> Unit, confirm: (String, Boolean) -> Unit) {
    if (progress != null && progress.stages.isNotEmpty()) ActivityProgressRow(progress, { openProgress(activity.id) })
    else ActivityRow(activity, open) {
        ExampleBadge(if (activity.confirmed) "참여 확정" else "신청함", accent = activity.confirmed)
        ConfirmToggle(activity) { confirm(activity.id, it) }
    }
}

/** Collapsed group header; the chevron turns at once under reduced motion. */
@Composable private fun EndedHeader(count: Int, expanded: Boolean, toggle: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, if (reduceMotion) snap() else spring(), label = "chevron")
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp)
        .clickable(role = Role.Button, onClick = toggle).semantics { heading(); stateDescription = if (expanded) "펼침" else "접힘" },
        verticalAlignment = Alignment.CenterVertically) {
        Text("끝난 활동 $count", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Label + switch as one `ListItem` row (contract "토글 라벨은 바로 옆"); the whole row toggles. */
@Composable internal fun ConfirmToggle(activity: ActivityState, change: (Boolean) -> Unit) =
    ListItem({ Text("참여 확정 표시") }, Modifier.toggleable(activity.confirmed, role = Role.Switch, onValueChange = change), trailingContent = { Switch(activity.confirmed, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
