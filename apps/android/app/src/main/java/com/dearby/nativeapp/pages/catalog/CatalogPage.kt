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

@Composable fun CatalogPage(state: CatalogState, open: (String) -> Unit, filter: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("활동 둘러보기", style = MaterialTheme.typography.headlineMedium)
            ExampleBadge()
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("전체", "참가등록형", "선발형")) { label ->
            FilterChip(state.filter == label, { filter(label) }, { Text(label) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = androidx.compose.ui.graphics.Color.White, containerColor = Soft), border = null)
        } } }
        items(state.visibleActivities, key = { it.id }) { activity -> ActivityCard(activity, open) }
    }
}

@Composable internal fun ActivityCard(activity: ActivityState, open: (String) -> Unit, footer: @Composable ColumnScope.() -> Unit = {}) {
    OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(activityArtwork(activity.id)), null, Modifier.width(112.dp).height(112.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(activity.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(activity.fields.first().second, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(15.dp), tint = Quiet); Text(activity.status + " · " + activity.participation.substringBefore(" ·"), style = MaterialTheme.typography.bodySmall, color = Quiet) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(Icons.Outlined.LocationOn, null, Modifier.size(15.dp), tint = Quiet); Text(activity.date.substringBefore("일") + "일 · " + activity.fields.first { it.first == "장소" }.second, style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2) }
                footer()
            }
        }
    }
}
