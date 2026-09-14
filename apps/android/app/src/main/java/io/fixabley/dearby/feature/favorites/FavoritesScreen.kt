package io.fixabley.dearby.feature.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog
import io.fixabley.dearby.entities.activitycatalog.model.Notice

@Composable
internal fun FavoritesScreen(catalog: ActivityCatalog, favoriteIds: Set<String>, onRemove: (String) -> Unit, showDetail: (Notice) -> Unit) {
    val organizations = catalog.organizations.filter { it.id in favoriteIds }
    if (organizations.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("저장한 조직이 없어요", style = MaterialTheme.typography.titleLarge)
            Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요.", Modifier.padding(top = 12.dp))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("이 기기에 저장돼요", style = MaterialTheme.typography.labelMedium) }
            items(organizations, key = { it.id }) { organization ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(organization.name, style = MaterialTheme.typography.titleMedium)
                        val ancestors = catalog.organizationPath(organization.id).dropLast(1)
                        if (ancestors.isNotEmpty()) {
                            Text(ancestors.joinToString(" › ") { it.name }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val notices = catalog.feed.filter { it.organizationId == organization.id }
                        Text(if (notices.isEmpty()) "현재 연결된 공고가 없어요" else "연결된 공고 ${notices.size}개",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        notices.forEach { notice ->
                            TextButton(onClick = { showDetail(notice) }, modifier = Modifier.testTag("favorite.notice.${notice.id}")) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(notice.title)
                                    Text(listOf(notice.categorySummary, catalog.contextNames(notice))
                                        .filter { it.isNotEmpty() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        TextButton(onClick = { onRemove(organization.id) }, modifier = Modifier.testTag("remove.${organization.id}")) {
                            Text("즐겨찾기에서 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
