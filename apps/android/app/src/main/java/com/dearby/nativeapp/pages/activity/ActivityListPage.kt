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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.activityCard.ActivityCard
import com.dearby.nativeapp.widgets.activity.programHeader.ProgramHeaderRow

/**
 * Discovery (contract "발견 프로그램 묶음"): programs with open activities as an accordion; a header toggles its open
 * activity cards. The next program page loads near the end.
 */
@Composable fun ActivityListPage(
    state: ActivityListState, open: (String) -> Unit, search: (String) -> Unit, retry: () -> Unit, loadMore: () -> Unit, openLink: (String) -> Unit,
    toggle: (String) -> Unit, retryProgram: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("활동 둘러보기", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), style = MaterialTheme.typography.headlineMedium)
        // Outside the list so the field keeps focus and the keyboard while results change.
        OutlinedTextField(state.query, search, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("activity-search"),
            placeholder = { Text("프로그램·조직·활동 이름으로 검색") }, singleLine = true, shape = RoundedCornerShape(11.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton({ search("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val programs = state.programs
            when {
                state.phase == ActivityListPhase.LOADING -> item { DearbyLoadingState("활동을 불러오는 중이에요.") }
                state.phase == ActivityListPhase.FAILED -> item { DearbyErrorState("활동을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry) }
                programs.isEmpty() && state.query.isNotBlank() -> item { DearbyEmptyState("검색 결과가 없어요", "다른 검색어로 찾아 보세요.") }
                programs.isEmpty() -> item { DearbyEmptyState("지금 모집 중인 프로그램이 없어요", "새 모집이 열리면 여기에서 볼 수 있어요.") }
                else -> {
                    programs.forEach { program ->
                        item(key = "program-" + program.id) {
                            ProgramHeaderRow(program.title, program.organization, program.openCount, program.deadline, program.id in state.expanded, onToggle = { toggle(program.id) })
                        }
                        if (program.id in state.expanded) {
                            val loaded = state.programActivities[program.id]
                            when (loaded?.phase) {
                                null, ProgramPhase.LOADING -> item(key = "loading-" + program.id) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                                }
                                ProgramPhase.FAILED -> item(key = "failed-" + program.id) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextButton({ retryProgram(program.id) }, Modifier.heightIn(min = 48.dp)) { Text("활동을 불러오지 못했어요 · 다시 시도") } }
                                }
                                ProgramPhase.LOADED -> {
                                    val activities = loaded.ids.mapNotNull(state.known::get)
                                    if (activities.isEmpty()) item(key = "empty-" + program.id) { Text("지금 열린 활동이 없어요.", Modifier.padding(start = 12.dp), color = Quiet, style = MaterialTheme.typography.bodySmall) }
                                    items(activities, key = { "activity-" + it.id }) { activity ->
                                        ActivityCard(activity.title, activity.organization, activity.summary, activity.status,
                                            activity.date.ifEmpty { "일정 미확인" } + " · " + (activity.location ?: "장소 미확인"), { open(activity.id) },
                                            Modifier.padding(start = 12.dp), imageUrl = activity.imageUrl, applyUrl = activity.applyUrl, recruitmentEnd = activity.recruitmentEnd, onApply = openLink)
                                    }
                                }
                            }
                        }
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

/** Activity row used by My Activities. */
@Composable internal fun ActivityRow(activity: ActivityState, open: (String) -> Unit, footer: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityImage(activity.imageUrl, Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(activity.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.organization, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = Quiet); Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodySmall, color = Quiet) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = Quiet); Text(activity.location ?: "장소 미확인", style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2) }
                footer()
            }
        }
    }
}

/** The activity's image when it has one, otherwise a calendar tile. Decorative: the text says the same. */
@Composable internal fun ActivityImage(url: String?, modifier: Modifier) =
    if (url != null) AsyncImage(url, null, modifier.background(Mint), contentScale = ContentScale.Crop)
    else Box(modifier.background(Mint), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Event, null, Modifier.size(40.dp), tint = Teal) }
