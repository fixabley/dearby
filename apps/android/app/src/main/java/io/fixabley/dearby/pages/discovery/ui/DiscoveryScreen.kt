package io.fixabley.dearby.pages.discovery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog
import io.fixabley.dearby.entities.activitycatalog.model.Notice
import io.fixabley.dearby.widgets.activitycard.ui.ActivityCard

@Composable
internal fun DiscoveryScreen(catalog: ActivityCatalog, favoriteIds: Set<String>, onSave: (String) -> Unit, showDetail: (Notice) -> Unit) {
    var feedback by remember { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { catalog.feed.size })
    Column(Modifier.fillMaxSize()) {
        Text("검토한 공고 샘플 · ${catalog.snapshotDate}",
            Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (catalog.feed.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("표시할 공고가 없어요") }
        } else {
            VerticalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discovery.pager"), key = { catalog.feed[it].id }) { index ->
                val notice = catalog.feed[index]
                val organization = catalog.organization(notice.organizationId)
                val save = {
                    if (organization != null) {
                        onSave(organization.id)
                        feedback = "${organization.name} 저장됨"
                    } else feedback = "저장할 조직을 확인 중이에요"
                }
                ActivityCard(notice, organization, catalog.contextNames(notice), organization?.id in favoriteIds,
                    "${index + 1} / ${catalog.feed.size}", save, { showDetail(notice) })
            }
        }
        Text(feedback.ifEmpty { "위아래로 넘기기 · 더블탭으로 조직 저장" },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("discovery.feedback"),
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}
