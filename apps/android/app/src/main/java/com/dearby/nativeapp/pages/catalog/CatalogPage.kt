package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun CatalogPage(state: CatalogState, open: (String) -> Unit, filter: (String) -> Unit) {
    val activities = state.visibleActivities
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("모집 중인 활동", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            Text("예시", color = Quiet, style = MaterialTheme.typography.labelSmall)
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("전체", "참가등록형", "선발형")) { label ->
            FilterChip(state.filter == label, { filter(label) }, { Text(label) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = androidx.compose.ui.graphics.Color.White, containerColor = Soft), border = null)
        } } }
        items(activities, key = { it.id }) { activity ->
            OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(92.dp).heightIn(min = 110.dp).background(Soft, RoundedCornerShape(8.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Image, null, tint = Quiet); Text("이미지 미제공", color = Quiet, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(activity.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(activity.summary, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = Quiet); Text(activity.date, style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Text(activity.status + " · " + activity.participation, style = MaterialTheme.typography.labelSmall, color = Teal)
                    }
                }
            }
        }
    }
}
