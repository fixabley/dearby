package io.fixabley.dearby.pages.favorites.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog
import io.fixabley.dearby.entities.activitycatalog.model.Notice
import io.fixabley.dearby.widgets.favoriteorganizationcard.ui.FavoriteOrganizationCard

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
                val notices = catalog.feed.filter { it.organizationId == organization.id }
                FavoriteOrganizationCard(
                    organization = organization,
                    ancestors = catalog.organizationPath(organization.id).dropLast(1),
                    notices = notices,
                    contextNames = notices.associate { it.id to catalog.contextNames(it) },
                    onRemove = onRemove,
                    showDetail = showDetail,
                )
            }
        }
    }
}
