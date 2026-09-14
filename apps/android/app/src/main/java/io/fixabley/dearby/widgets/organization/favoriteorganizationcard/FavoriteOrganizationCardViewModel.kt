package io.fixabley.dearby.widgets.organization.favoriteorganizationcard

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.fixabley.dearby.entities.notice.api.NoticeRepository
import io.fixabley.dearby.entities.organization.api.OrganizationRepository
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState

internal class FavoriteOrganizationCardViewModel(private val id: String, private val noticeIds: List<String>,
    private val notices: NoticeRepository, private val organizations: OrganizationRepository, private val favorites: FavoritesState) {
    private val content by derivedStateOf {
        notices.revision; organizations.revision
        organizations.find(id)?.let { organization ->
            val rows = noticeIds.mapNotNull(notices::find).filter { it.organizationId == id }.map { notice ->
                val context = notice.contexts.distinctBy { it.organizationId }.mapNotNull { organizations.find(it.organizationId)?.name }.joinToString(" · ")
                FavoriteNoticeState(notice.id, notice.title, listOf(notice.categorySummary, context).filter { it.isNotEmpty() }.joinToString(" · "))
            }
            FavoriteOrganizationCardState(id, organization.name, organizations.path(id).dropLast(1).map { it.name }, rows)
        }
    }
    val state: FavoriteOrganizationCardState? get() = if (id in favorites.ids) content else null
    fun remove() = favorites.remove(id)
}
