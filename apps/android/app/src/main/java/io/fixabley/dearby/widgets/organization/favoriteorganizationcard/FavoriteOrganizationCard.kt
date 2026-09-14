package io.fixabley.dearby.widgets.organization.favoriteorganizationcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCardState
import io.fixabley.dearby.entities.notice.ui.NoticeClassification

@Composable
internal fun FavoriteOrganizationCard(
    state: FavoriteOrganizationCardState,
    onRemove: (String) -> Unit,
    showDetail: (String) -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(state.name, style = MaterialTheme.typography.titleMedium)
            if (state.ancestorNames.isNotEmpty()) {
                Text(state.ancestorNames.joinToString(" › "), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (state.notices.isEmpty()) "현재 연결된 공고가 없어요" else "연결된 공고 ${state.notices.size}개",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.notices.forEach { notice ->
                TextButton(onClick = { showDetail(notice.id) }, modifier = Modifier.testTag("favorite.notice.${notice.id}")) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Text(notice.title)
                        NoticeClassification(notice.classification)
                    }
                }
            }
            TextButton(onClick = { onRemove(state.id) }, modifier = Modifier.testTag("remove.${state.id}")) {
                Text("즐겨찾기에서 삭제", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
