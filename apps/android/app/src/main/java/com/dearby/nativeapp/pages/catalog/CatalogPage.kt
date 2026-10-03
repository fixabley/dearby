package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.R
import com.dearby.nativeapp.shared.ui.*

internal fun activityArtwork(id: String) = when (id) {
    "camp" -> R.drawable.prototype_camp
    "meetup" -> R.drawable.prototype_meetup
    else -> R.drawable.prototype_conference
}

@Composable fun CatalogPage(state: CatalogState, open: (String) -> Unit, filter: (String) -> Unit, save: (String) -> Unit, savedOnly: Boolean = false) {
    val activities = state.visibleActivities.filter { !savedOnly || it.saved || it.organizationSaved }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (savedOnly) "저장한 활동" else "활동 둘러보기", style = MaterialTheme.typography.headlineMedium)
            ExampleBadge()
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("전체", "참가등록형", "선발형")) { label ->
            FilterChip(state.filter == label, { filter(label) }, { Text(label) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = androidx.compose.ui.graphics.Color.White, containerColor = Soft), border = null)
        } } }
        if (activities.isEmpty()) item { Text("저장한 활동이 없어요. 발견에서 관심 있는 활동을 골라보세요.", Modifier.padding(vertical = 28.dp), color = Quiet) }
        items(activities, key = { it.id }) { activity ->
            OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(activityArtwork(activity.id)), null, Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(activity.title, Modifier.weight(1f).padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton({ save(activity.id) }, Modifier.size(36.dp)) { Icon(if (activity.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (activity.saved) "${activity.title} 저장 해제" else "${activity.title} 저장", tint = if (activity.saved) Teal else Quiet) }
                        }
                        Text(activity.fields.first().second, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = Quiet); Text(activity.status + " · " + activity.participation.substringBefore(" ·"), style = MaterialTheme.typography.bodySmall, color = Quiet) }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = Quiet); Text(activity.date.substringBefore("일") + "일 · " + activity.fields.first { it.first == "장소" }.second, style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2) }
                    }
                }
            }
        }
    }
}
