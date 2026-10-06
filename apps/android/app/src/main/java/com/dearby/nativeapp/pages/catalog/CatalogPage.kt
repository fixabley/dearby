package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.activityCard.ActivityCard

@Composable fun CatalogPage(state: CatalogState, open: (String) -> Unit, filter: (String) -> Unit, retry: () -> Unit, openLink: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("활동 둘러보기", style = MaterialTheme.typography.headlineMedium) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("전체", "바로 신청", "선발형")) { label ->
            FilterChip(state.filter == label, { filter(label) }, { Text(label) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = androidx.compose.ui.graphics.Color.White, containerColor = Soft), border = null)
        } } }
        when {
            state.phase == CatalogPhase.LOADING -> item { DearbyLoadingState("활동을 불러오는 중이에요.") }
            state.phase == CatalogPhase.FAILED -> item { DearbyErrorState("활동을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry) }
            state.visibleActivities.isEmpty() -> item { DearbyEmptyState("모집 중인 활동이 없어요", "새 활동이 공개되면 여기에서 볼 수 있어요.") }
            else -> items(state.visibleActivities, key = { it.id }) { activity ->
                // Catalog activities have no photos; the card draws its type and organization tile.
                ActivityCard(activity.title, activity.organization ?: "주최 정보 미확인", activity.summary, activity.status + " · " + activity.participation,
                    activity.date + " · " + (activity.location ?: "장소 미확인"), { open(activity.id) },
                    isSelection = activity.participation == "선발형", applyUrl = activity.quickApplyUrl, recruitmentEnd = activity.recruitmentEnd, onApply = openLink)
            }
        }
    }
}

/** Activity row used by My Activities. */
@Composable internal fun ActivityRow(activity: ActivityState, open: (String) -> Unit, footer: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityPlaceholder(Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(activity.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.organization ?: activity.summary, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = Quiet); Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodySmall, color = Quiet) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = Quiet); Text(activity.location ?: "장소 미확인", style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2) }
                footer()
            }
        }
    }
}

/** Catalog activities have no images; a calendar tile instead of an example photo. */
@Composable internal fun ActivityPlaceholder(modifier: Modifier) = Box(modifier.background(Mint), contentAlignment = Alignment.Center) {
    Icon(Icons.Outlined.Event, null, Modifier.size(40.dp), tint = Teal)
}
