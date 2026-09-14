package io.fixabley.dearby.pages.favorites.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCardState
import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCard

@Composable
internal fun FavoritesScreen(organizations: List<FavoriteOrganizationCardState>, onRemove: (String) -> Unit, showDetail: (String) -> Unit) {
    if (organizations.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(Spacing.extraLarge), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("저장한 조직이 없어요", style = MaterialTheme.typography.titleLarge)
            Text("발견 탭의 공고를 더블탭하면 조직이 여기에 저장돼요.", Modifier.padding(top = Spacing.medium))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.extraLarge), verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            item { Text("이 기기에 저장돼요", style = MaterialTheme.typography.labelMedium) }
            items(organizations, key = { it.id }) { organization ->
                FavoriteOrganizationCard(organization, onRemove, showDetail)
            }
        }
    }
}
