package io.fixabley.dearby.widgets.favoriteorganizationcard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.entities.noticecatalog.model.Notice
import io.fixabley.dearby.entities.noticecatalog.model.Organization
import io.fixabley.dearby.entities.noticecatalog.ui.NoticeClassification

@Composable
internal fun FavoriteOrganizationCard(
    organization: Organization,
    ancestors: List<Organization>,
    notices: List<Notice>,
    contextNames: Map<String, String>,
    onRemove: (String) -> Unit,
    showDetail: (Notice) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(organization.name, style = MaterialTheme.typography.titleMedium)
            if (ancestors.isNotEmpty()) {
                Text(ancestors.joinToString(" › ") { it.name }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (notices.isEmpty()) "현재 연결된 공고가 없어요" else "연결된 공고 ${notices.size}개",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            notices.forEach { notice ->
                TextButton(onClick = { showDetail(notice) }, modifier = Modifier.testTag("favorite.notice.${notice.id}")) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notice.title)
                        NoticeClassification(notice, contextNames[notice.id].orEmpty())
                    }
                }
            }
            TextButton(onClick = { onRemove(organization.id) }, modifier = Modifier.testTag("remove.${organization.id}")) {
                Text("즐겨찾기에서 삭제", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
