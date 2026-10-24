package com.dearby.nativeapp.pages.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.activityCard.ActivityCard
import com.dearby.nativeapp.widgets.activity.programCard.ProgramCard

/**
 * Discovery (contract "발견 프로그램 묶음" · "발견 카드 리뉴얼"): one program card per program; its head toggles the activity cards that
 * expand inside the same card. The next program page loads near the end.
 * Scrolling (contract "발견 탭 스크롤 동작"): the title line stays fixed, the search area collapses on the way down and re-enters at once on
 * the way up, the list pulls to refresh below it, and a 맨 위로 button appears after about a screen.
 * Keyboard (contract "폼은 플랫폼 기본 컴포넌트" · "키보드 내리기"): the search field is the Material 3 search-bar input; a tap outside any
 * field or a scroll of the list clears focus, which hides the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActivityListPage(
    state: ActivityListState, open: (String) -> Unit, search: (String) -> Unit, retry: () -> Unit, refresh: () -> Unit, loadMore: () -> Unit,
    toggle: (String) -> Unit, retryProgram: (String) -> Unit, setOpenOnly: (Boolean) -> Unit = {}, notify: () -> Unit = {}, clearRefreshFailure: () -> Unit = {}, settings: () -> Unit = {},
    /** The 추천 묶음 above the programs (contract "발견 탭 노출"), or null when there is none; hidden while searching. */
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    // Pull to refresh failed with a list showing: the list stays, a snackbar passes once (iOS parity); the flag is cleared even if the page leaves mid-show.
    LaunchedEffect(state.refreshFailed) { if (state.refreshFailed) try { snackbar.showSnackbar("새로 고치지 못했어요") } finally { clearRefreshFailure() } }
    val collapse = remember(reduceMotion, focusManager) { SearchAreaCollapse(reduceMotion, scope, focusManager::clearFocus) }
    // The program list is drawn only with programs; loading, failure and empty results are centred states in its place (contract "비스크롤 화면 배치·CTA").
    val listShown = state.phase == ActivityListPhase.LOADED && state.programs.isNotEmpty()
    // The list state keeps its last values while a centred state replaces the list, so the read is gated on the list being shown.
    val farDown by remember(listShown) { derivedStateOf { listShown && listState.pastOneScreen() } }
    // Pinned top bar (contract "iOS 상단 바…" Android): the bar tints once anything scrolls under it — the collapse connection's consumption counts too.
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    // Fields and buttons take their own taps first, so this only catches taps on empty space.
    Box(Modifier.fillMaxSize().imePadding().nestedScroll(topBar.nestedScrollConnection).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
        Column(Modifier.fillMaxSize()) {
            DearbyTopBar("활동 둘러보기", scrollBehavior = topBar) { TabActions(settings, notify) }
            // Search field and 마감 숨기기 switch: outside the list so the field keeps focus and the keyboard while results change.
            Column(Modifier.fillMaxWidth().collapsing(collapse)) {
                DearbySearchField(state.query, search, "프로그램·조직·활동 이름으로 검색",
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp).testTag("activity-search").onFocusChanged { collapse.locked = it.isFocused; if (it.isFocused) collapse.expand() })
                // 마감 숨기기 (contract "모집 중인 프로그램만 보기"·"토글 라벨은 바로 옆"): label + switch as one left-aligned toggleable unit; off also lists programs active in the last two years.
                Row(Modifier.padding(start = 12.dp, end = 20.dp, bottom = 4.dp).heightIn(min = 48.dp).toggleable(state.openOnly, role = Role.Switch, onValueChange = setOpenOnly).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("마감 숨기기", style = MaterialTheme.typography.bodyLarge)
                    Switch(state.openOnly, null)
                }
            }
            // The collapse connection sits outside the pull gesture: a pull at the top re-enters the search area first, then the indicator grows below it.
            PullToRefreshBox(state.refreshing, refresh, Modifier.fillMaxSize().nestedScroll(collapse.connection)) {
                val programs = state.programs
                // The centred states fill the list area (they scroll only when they overflow, and pull-to-refresh still works through that scroll).
                when {
                    state.phase == ActivityListPhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { repeat(4) { ProgramCardSkeleton() } }
                    state.phase == ActivityListPhase.FAILED -> DearbyErrorState("활동을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
                    programs.isEmpty() && state.query.isNotBlank() -> DearbyEmptyState("검색 결과가 없어요", "다른 검색어로 찾아 보세요.", Modifier.fillMaxSize(), Icons.Rounded.Search)
                    programs.isEmpty() && state.openOnly -> DearbyEmptyState("지금 모집 중인 프로그램이 없어요", "‘마감 숨기기’를 끄면 최근 2년 안에 활동한 프로그램도 보여요.", Modifier.fillMaxSize())
                    programs.isEmpty() -> DearbyEmptyState("최근 2년 안에 활동한 프로그램이 없어요", "새 모집이 열리면 여기에서 볼 수 있어요.", Modifier.fillMaxSize())
                    else -> LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (header != null && state.query.isBlank()) item(key = "recommendation") { header() }
                        items(programs, key = { "program-" + it.id }) { program ->
                            ProgramCard(program.title, program.organization, program.openCount, program.upcomingCount, program.deadline, program.nearestStart, program.latestActivityAt,
                                program.tags, program.id in state.expanded, { toggle(program.id) }, logoUrl = program.logoUrl) { ProgramBody(program.id, state, open, retryProgram) }
                        }
                        if (state.nextCursor != null) item(key = "more") {
                            // Shown as the list nears its end; each new page re-arms it.
                            LaunchedEffect(programs.size) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                if (state.moreFailed) TextButton(loadMore, Modifier.heightIn(min = 48.dp)) { Text("다음 프로그램을 불러오지 못했어요 · 다시 시도") }
                                else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
        // 맨 위로: fades in past about one screen; the jump is instant and the search area comes back with it.
        AnimatedVisibility(farDown, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
            enter = fadeIn(if (reduceMotion) snap() else spring()), exit = fadeOut(if (reduceMotion) snap() else spring())) {
            SmallFloatingActionButton({ scope.launch { listState.scrollToItem(0) }; collapse.expand() }) { Icon(Icons.Rounded.ArrowUpward, "맨 위로") }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Roughly a screen down: the items before the first visible one, taken at the visible items' average height, exceed the viewport. */
private fun LazyListState.pastOneScreen(): Boolean {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return false
    val average = visible.sumOf { it.size } / visible.size
    return firstVisibleItemIndex * average + firstVisibleItemScrollOffset > info.viewportSize.height
}

/**
 * "Enter always" collapse of the search area, the rule of `TopAppBar`'s `enterAlwaysScrollBehavior` on a custom header: [offset] runs
 * from 0 (shown) to -[height] (folded away) and consumes scroll in both directions before the list moves, so the area folds as soon as
 * the user scrolls down and re-enters as soon as they scroll up. A released drag settles the way it was going with a short spring;
 * under reduced motion the area switches at once. [locked] while the search field has focus; a user scroll then [unfocus]es it
 * (the keyboard goes down) instead of folding.
 */
@Stable private class SearchAreaCollapse(private val reduceMotion: Boolean, private val scope: CoroutineScope, private val unfocus: () -> Unit) {
    var offset by mutableFloatStateOf(0f); private set
    var height by mutableIntStateOf(0)
    var locked = false
    private var settling: Job? = null
    private var direction = 0f
    fun expand() { settling?.cancel(); offset = 0f }
    private fun settle(target: Float) { settling = scope.launch { animate(offset, target, animationSpec = spring(stiffness = 600f)) { value, _ -> offset = value } } }
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (locked) { if (source == NestedScrollSource.UserInput) unfocus(); return Offset.Zero }
            if (height == 0 || available.y == 0f) return Offset.Zero
            settling?.cancel()
            direction = available.y
            if (reduceMotion) { offset = if (available.y < 0) -height.toFloat() else 0f; return Offset.Zero }
            val previous = offset
            offset = (previous + available.y).coerceIn(-height.toFloat(), 0f)
            return Offset(0f, offset - previous)
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!reduceMotion && offset != 0f && offset != -height.toFloat()) settle(if (direction > 0) 0f else -height.toFloat())
            return Velocity.Zero
        }
    }
}

/** Lays the content out at its full height minus the folded part, pushed up under the title and fading with it. */
private fun Modifier.collapsing(state: SearchAreaCollapse): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    state.height = placeable.height
    val shown = (placeable.height + state.offset).roundToInt().coerceAtLeast(0)
    layout(placeable.width, shown) { placeable.place(0, shown - placeable.height) }
}.graphicsLayer { alpha = if (state.height == 0) 1f else 1f + state.offset / state.height }

/** The expanded program's activities (loading, failed, empty, then the 모집 중 category groups and the 모집 예정/지난 활동 groups). */
@Composable private fun ProgramBody(programId: String, state: ActivityListState, open: (String) -> Unit, retryProgram: (String) -> Unit) {
    val loaded = state.programActivities[programId]
    when (loaded?.phase) {
        // Kept until both the open list and the 모집 예정·지난 활동 list arrive (loadProgram goes LOADED once, after both), so no empty-state flash.
        null, ProgramPhase.LOADING -> DearbySkeleton(Modifier.fillMaxWidth()) { repeat(2) { ActivityCardSkeleton() } }
        ProgramPhase.FAILED -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton({ retryProgram(programId) }, Modifier.heightIn(min = 48.dp)) { Text("활동을 불러오지 못했어요 · 다시 시도") } }
        ProgramPhase.LOADED -> {
            val activities = loaded.ids.mapNotNull(state.known::get)
            // "지금 모집 중인 활동이 없어요" only when every shown group is empty (contract "마감 숨기기", 2026-10-19 밤 정정).
            if (activities.isEmpty() && loaded.upcomingIds.none(state.known::containsKey) && loaded.pastIds.none(state.known::containsKey)) Text("지금 모집 중인 활동이 없어요", Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            // 활동 종류별 묶음 (contract "활동 종류"): contract order, 미분류 last; no heading when only one kind.
            // Within a group: recruitment deadline ascending, no deadline last, ties by name (2026-10-19 "정렬은 모집일 순서로").
            val groups = activities.groupBy { it.category }.entries.sortedBy { (_, group) -> group.first().categoryOrder }
            groups.forEach { (category, unsorted) ->
                if (groups.size > 1) GroupHeading(category ?: "미분류")
                unsorted.sortedWith(compareBy<ActivityState, Instant?>(nullsLast()) { it.sortKey }.thenBy { it.title }).forEach { Card(it, open) }
            }
            // 모집 예정 (always) · 지난 활동 (switch off only): no category headings; an empty group is omitted.
            listOf("모집 예정" to loaded.upcomingIds, "지난 활동" to loaded.pastIds).forEach { (heading, ids) ->
                val rows = ids.mapNotNull(state.known::get)
                if (rows.isNotEmpty()) { GroupHeading(heading); rows.forEach { Card(it, open) } }
            }
        }
    }
}

/** A collapsed [ProgramCard]'s outline: logo, title and organization lines (contract "내용 로딩은 스켈레톤"). */
@Composable private fun ProgramCardSkeleton() =
    Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest), RoundedCornerShape(20.dp)).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.size(44.dp), RoundedCornerShape(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SkeletonLine(.6f, 18.dp); SkeletonLine(.35f, 12.dp) }
    }

/** An [ActivityCard]'s outline: title and chip, then the 모집·활동 rows. */
@Composable private fun ActivityCardSkeleton() =
    Column(Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.weight(1f)) { SkeletonLine(.7f, 18.dp) }; SkeletonBlock(Modifier.size(52.dp, 24.dp), RoundedCornerShape(8.dp)) }
        SkeletonLine(.55f, 12.dp)
        SkeletonLine(.45f, 12.dp)
    }

@Composable private fun GroupHeading(text: String) =
    Text(text, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp).semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)

@Composable private fun Card(activity: ActivityState, open: (String) -> Unit) =
    ActivityCard(activity.title, activity.status, activity.recruiting, activity.period, activity.date, activity.location, { open(activity.id) })

/** Activity row used by My Activities. */
@Composable internal fun ActivityRow(activity: ActivityState, open: (String) -> Unit, trailing: @Composable () -> Unit = {}, footer: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityImage(activity.imageUrl, Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(activity.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.organization, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Rounded.Event, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Rounded.Place, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(activity.location ?: "장소 미확인", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                footer()
            }
            trailing()
        }
    }
}

/** The activity's image when it has one, otherwise a calendar tile. Decorative: the text says the same. */
@Composable internal fun ActivityImage(url: String?, modifier: Modifier) =
    if (url != null) AsyncImage(url, null, modifier.background(MaterialTheme.colorScheme.primaryContainer), contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Event, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
