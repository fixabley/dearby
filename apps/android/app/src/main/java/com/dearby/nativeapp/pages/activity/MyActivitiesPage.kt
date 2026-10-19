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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable fun MyActivitiesPage(state: ActivityListState, open: (String) -> Unit, openProgress: (String) -> Unit, confirm: (String, Boolean) -> Unit, explore: () -> Unit, notify: () -> Unit = {}) {
    val active = state.activeActivities
    val ended = state.endedActivities
    var showEnded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        // Tab title line shared by the five tabs (contract "탭 제목 통일·머리 간소화"); the 예시 badge keeps its place beside the title.
        ScreenHeader("내 활동") {
            ExampleBadge()
            IconButton(notify) { Icon(Icons.Outlined.NotificationsNone, "알림") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (active.isEmpty() && ended.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("신청한 활동이 없어요. 발견에서 관심 있는 활동을 신청해 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DearbyOutlineButton(explore) { Text("활동 둘러보기") }
                }
            } else item { Text(PROGRESS_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
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
        Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun ConfirmToggle(activity: ActivityState, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(activity.confirmed, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Text("참여 확정 표시", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(activity.confirmed, null)
    }
}
