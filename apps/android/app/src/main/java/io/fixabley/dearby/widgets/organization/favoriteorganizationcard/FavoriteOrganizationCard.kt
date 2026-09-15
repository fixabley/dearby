package io.fixabley.dearby.widgets.organization.favoriteorganizationcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                        Text(notice.title, style = MaterialTheme.typography.titleSmall)
                        NoticeClassification(notice.classification)
                    }
                    IconButton(onClick = { showDetail(notice.id) }, modifier = Modifier.testTag("favorite.notice.${notice.id}")) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = "${notice.title} 상세 보기")
                    }
                }
            }
            IconButton(onClick = { onRemove(state.id) }, modifier = Modifier.testTag("remove.${state.id}")) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "${state.name} 즐겨찾기에서 삭제", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
