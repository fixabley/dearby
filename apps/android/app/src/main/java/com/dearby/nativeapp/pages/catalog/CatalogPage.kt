package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun CatalogPage(state: CatalogState, saved: Boolean, refresh: () -> Unit, open: (String) -> Unit, removeGroup: (SavedGroupState) -> Unit) {
    val activities = state.activities.filter { if (saved) it.programSaved || it.organizationSaved else it.current }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(if (saved) "저장한 활동" else "지금 모집 중", style = MaterialTheme.typography.headlineMedium) }
        item { Text(if (saved) "프로그램과 조직을 이 기기에 저장합니다. 계정 동기화는 지원하지 않습니다." else "공식 출처에서 모집이 확인된 활동을 살펴보세요.") }
        item { OutlinedButton(refresh, enabled = !state.loading) { Text(if (state.loading) "불러오는 중…" else "새로고침") } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.storageError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.cached) item { Text("기기에 보관한 자료입니다. 유효기간이 지난 활동은 모집 중 목록에서 제외됩니다.") }
        state.generatedAt?.let { item { Text("자료 생성: $it", style = MaterialTheme.typography.bodySmall) } }
        if (saved) items(state.savedGroups, key = { "${it.organization}:${it.id}" }) { group ->
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(if (group.organization) "저장한 조직" else "저장한 프로그램", style = MaterialTheme.typography.labelMedium)
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                TextButton({ removeGroup(group) }, enabled = state.storageReady && !state.writing) { Text("저장 해제") }
            } }
        }
        if (!state.loading && activities.isEmpty()) item {
            Text(if (saved) "저장한 대상의 활동이 없습니다." else if (state.error != null) "현재 확인 가능한 모집을 불러오지 못했습니다." else "현재 모집 중으로 확인된 활동이 없습니다.")
        }
        items(activities, key = { it.id }) { activity ->
            OutlinedCard(onClick = { open(activity.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activity.organization, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(activity.title, style = MaterialTheme.typography.titleLarge)
                    Text(activity.status + " · " + activity.participation, style = MaterialTheme.typography.labelMedium)
                    Text(activity.summary, style = MaterialTheme.typography.bodyMedium)
                    Text(activity.date)
                    Text("출처 확인: ${activity.checked}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
