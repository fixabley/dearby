package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.activityCard.ActivityCard
import com.dearby.nativeapp.widgets.activity.programCard.ProgramCard

/**
 * Discovery (contract "발견 프로그램 묶음" · "발견 카드 리뉴얼"): one program card per program; its head toggles the activity cards that
 * expand inside the same card. The next program page loads near the end.
 */
@Composable fun ActivityListPage(
    state: ActivityListState, open: (String) -> Unit, search: (String) -> Unit, retry: () -> Unit, loadMore: () -> Unit,
    toggle: (String) -> Unit, retryProgram: (String) -> Unit, setOpenOnly: (Boolean) -> Unit = {}, notify: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        // Tab title line shared by the five tabs (contract "탭 제목 통일·머리 간소화"); the search field follows at once.
        ScreenHeader("활동 둘러보기") { IconButton(notify) { Icon(Icons.Outlined.NotificationsNone, "알림") } }
        // Outside the list so the field keeps focus and the keyboard while results change.
        OutlinedTextField(state.query, search, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp).testTag("activity-search"),
            placeholder = { Text("프로그램·조직·활동 이름으로 검색") }, singleLine = true, shape = RoundedCornerShape(11.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton({ search("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } })
        // 마감 숨기기 (contract "모집 중인 프로그램만 보기"): off also lists programs active in the last two years.
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text("마감 숨기기", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Switch(state.openOnly, setOpenOnly, Modifier.padding(start = 8.dp).semantics { contentDescription = "마감 숨기기" })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val programs = state.programs
            when {
                state.phase == ActivityListPhase.LOADING -> item { DearbyLoadingState("활동을 불러오는 중이에요.") }
                state.phase == ActivityListPhase.FAILED -> item { DearbyErrorState("활동을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry) }
                programs.isEmpty() && state.query.isNotBlank() -> item { DearbyEmptyState("검색 결과가 없어요", "다른 검색어로 찾아 보세요.") }
                programs.isEmpty() && state.openOnly -> item { DearbyEmptyState("지금 모집 중인 프로그램이 없어요", "새 모집이 열리면 여기에서 볼 수 있어요. ‘마감 숨기기’를 끄면 최근 2년 안에 활동한 프로그램도 보여요.") }
                programs.isEmpty() -> item { DearbyEmptyState("최근 2년 안에 활동한 프로그램이 없어요", "새 모집이 열리면 여기에서 볼 수 있어요.") }
                else -> {
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
}

/** The expanded program's activities (loading, failed, empty, then the 모집 중 category groups and the 모집 예정/지난 활동 groups). */
@Composable private fun ProgramBody(programId: String, state: ActivityListState, open: (String) -> Unit, retryProgram: (String) -> Unit) {
    val loaded = state.programActivities[programId]
    when (loaded?.phase) {
        null, ProgramPhase.LOADING -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
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

@Composable private fun GroupHeading(text: String) =
    Text(text, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp).semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)

@Composable private fun Card(activity: ActivityState, open: (String) -> Unit) =
    ActivityCard(activity.title, activity.status, activity.recruiting, activity.period, activity.date, activity.location, { open(activity.id) })

/** Activity row used by My Activities. */
@Composable internal fun ActivityRow(activity: ActivityState, open: (String) -> Unit, footer: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityImage(activity.imageUrl, Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(activity.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.organization, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(activity.location ?: "장소 미확인", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                footer()
            }
        }
    }
}

/** The activity's image when it has one, otherwise a calendar tile. Decorative: the text says the same. */
@Composable internal fun ActivityImage(url: String?, modifier: Modifier) =
    if (url != null) AsyncImage(url, null, modifier.background(MaterialTheme.colorScheme.primaryContainer), contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Event, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
