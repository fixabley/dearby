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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun CatalogPage(state: CatalogState, saved: Boolean, refresh: () -> Unit, open: (String) -> Unit, removeGroup: (SavedGroupState) -> Unit, saveProgram: (String) -> Unit = {}) {
    var type by rememberSaveable { mutableStateOf("전체") }
    val activities = state.activities.filter { if (saved) it.programSaved || it.organizationSaved else it.current }.filter { type == "전체" || it.participation.startsWith(type) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (saved) "저장한 활동" else "모집 중인 활동", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            TextButton(refresh, enabled = !state.loading) { Text(if (state.loading) "불러오는 중…" else "새로고침", style = MaterialTheme.typography.labelSmall) }
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("전체", "참가등록형", "선발형")) { label ->
            FilterChip(type == label, { type = label }, { Text(label) }, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal, selectedLabelColor = androidx.compose.ui.graphics.Color.White, containerColor = Soft), border = null)
        } } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.storageError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.cached) item { Text("기기에 보관한 자료입니다. 유효기간이 지난 활동은 모집 중 목록에서 제외됩니다.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
        if (saved) items(state.savedGroups, key = { "${it.organization}:${it.id}" }) { group ->
            OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (group.organization) "저장한 조직" else "저장한 프로그램", color = Teal, style = MaterialTheme.typography.labelMedium); Text(group.title, style = MaterialTheme.typography.titleMedium) }
                TextButton({ removeGroup(group) }, enabled = state.storageReady && !state.writing) { Text("저장 해제") }
            } }
        }
        if (!state.loading && activities.isEmpty()) item { Text(if (saved) "저장한 대상의 활동이 없습니다." else if (state.error != null) "현재 확인 가능한 모집을 불러오지 못했습니다." else "현재 모집 중으로 확인된 활동이 없습니다.", Modifier.padding(vertical = 24.dp), color = Quiet) }
        items(activities, key = { it.id }) { activity ->
            OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(92.dp).heightIn(min = 110.dp).background(Soft, RoundedCornerShape(8.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Image, null, tint = Quiet); Text("이미지 미제공", color = Quiet, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(activity.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton({ saveProgram(activity.programId) }, enabled = state.storageReady && !state.writing, modifier = Modifier.size(48.dp)) { Icon(if (activity.programSaved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (activity.programSaved) "프로그램 저장 해제" else "프로그램 저장", tint = if (activity.programSaved) Teal else Quiet) }
                        }
                        Text(activity.summary, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = Quiet); Text(activity.date, style = MaterialTheme.typography.bodySmall, color = Quiet, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        Text(activity.status + " · " + activity.participation, style = MaterialTheme.typography.labelSmall, color = Teal)
                    }
                }
            }
        }
        state.generatedAt?.let { item { Text("자료 생성: $it", color = Quiet, style = MaterialTheme.typography.bodySmall) } }
    }
}
