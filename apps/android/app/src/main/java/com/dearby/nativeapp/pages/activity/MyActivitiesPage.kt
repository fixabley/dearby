package com.dearby.nativeapp.pages.activity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
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
 * stage over) collapsed below. An activity without stages keeps the plain `신청함` row. A track row opens
 * [openProgress]; a plain row opens the detail with [open]. The applied marks survive discovery searches (README rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MyActivitiesPage(state: ActivityListState, open: (String) -> Unit, openProgress: (String) -> Unit, explore: () -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {}, remove: (String) -> Unit = {}) {
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
            items(active, key = { it.id }) { MyActivityRow(it, state.progress[it.id], open, openProgress, remove) }
            if (ended.isNotEmpty()) {
                item { EndedHeader(ended.size, showEnded) { showEnded = !showEnded } }
                if (showEnded) items(ended, key = { it.id }) { MyActivityRow(it, state.progress[it.id], open, openProgress, remove) }
            }
        }
    }
}

internal const val PROGRESS_NOTE = "단계 상태는 내가 남기는 것이며 주최 측 결과가 아니에요."

/**
 * A track row, the plain row, or a "삭제된 활동" row (its activity is gone; contract "내 활동 서버 저장"), each with a trailing `더 보기` menu
 * holding 내 활동에서 빼기 (contract "빼기는 눈에 보이게"). Swiping the row from end to start (Material 3 `SwipeToDismissBox`, contract
 * "내 활동 목록에서 밀어서 지우기") asks the same confirmation; the row springs back whether it is confirmed or cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MyActivityRow(activity: ActivityState, progress: ProgressState?, open: (String) -> Unit, openProgress: (String) -> Unit, remove: (String) -> Unit) {
    var removing by remember { mutableStateOf(false) }
    // Returning false keeps the row in place: the dialog decides, and the list drops the row once it is removed.
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) removing = true; false })
    SwipeToDismissBox(swipe, enableDismissFromStartToEnd = false, backgroundContent = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Rounded.Delete, "내 활동에서 빼기", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }) {
        val more: @Composable () -> Unit = { RemoveMenu { remove(activity.id) } }
        when {
            activity.deleted -> DeletedRow(more)
            progress != null && progress.stages.isNotEmpty() -> ActivityProgressRow(progress, { openProgress(activity.id) }, trailing = more)
            else -> ActivityRow(activity, open, trailing = more) { ExampleBadge("신청함") }
        }
    }
    if (removing) RemoveFromMineDialog({ removing = false; remove(activity.id) }) { removing = false }
}

/** The activity was removed from Dearby; the record stays until the user takes it out. */
@Composable private fun DeletedRow(trailing: @Composable () -> Unit) =
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        ListItem({ Text("삭제된 활동") }, supportingContent = { Text("더 이상 볼 수 없는 활동이에요") }, trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }

@Composable private fun RemoveMenu(remove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Box {
        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "더 보기") }
        DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("내 활동에서 빼기", color = MaterialTheme.colorScheme.error) }, { menu = false; removing = true }) }
    }
    if (removing) RemoveFromMineDialog({ removing = false; remove() }) { removing = false }
}

/** The confirmation every 내 활동에서 빼기 goes through (detail, list row, progress page). */
@Composable internal fun RemoveFromMineDialog(remove: () -> Unit, dismiss: () -> Unit) =
    AlertDialog(onDismissRequest = dismiss, title = { Text("내 활동에서 뺄까요?") }, text = { Text("단계 상태도 함께 지워져요.") },
        confirmButton = { TextButton(remove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("빼기") } },
        dismissButton = { TextButton(dismiss) { Text("취소") } })

/** Visible error-coloured text button that asks [RemoveFromMineDialog] before [remove] (detail and progress page). */
@Composable internal fun RemoveFromMineButton(remove: () -> Unit, modifier: Modifier = Modifier) {
    var removing by remember { mutableStateOf(false) }
    TextButton({ removing = true }, modifier, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("내 활동에서 빼기") }
    if (removing) RemoveFromMineDialog({ removing = false; remove() }) { removing = false }
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

