package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.features.addtocalendar.model.applicationCalendarDraft
import io.fixabley.dearby.features.addtocalendar.model.phaseCalendarDraft
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.fixabley.dearby.entities.notice.api.NoticeRepository
import io.fixabley.dearby.entities.notice.model.NoticeContext
import io.fixabley.dearby.entities.organization.api.OrganizationRepository
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState

internal class NoticeDetailViewModel(private val id: String, private val notices: NoticeRepository,
    private val organizations: OrganizationRepository, private val favorites: FavoritesState) {
    private val content by derivedStateOf {
        notices.revision; organizations.revision
        notices.find(id)?.let { notice ->
            val path = organizations.path(notice.organizationId)
            fun resolve(ref: NoticeContext) = ResolvedOrganizationRole(ref.organizationId, ref.role, ref.label, organizations.find(ref.organizationId)?.name)
            NoticeDetailState(notice.id, notice.title, notice.aiDescription, notice.descriptionProvenance,
                notice.organizationId, path.lastOrNull { it.id == notice.organizationId }?.name, path.dropLast(1).map { it.name },
                notice.organizationLinks.map(::resolve), notice.categoryPath, notice.categorySummary,
                notice.contexts.map(::resolve), notice.edition, notice.targetUser, notice.participationCondition,
                notice.applicationInformation, notice.schedules.map { NoticeScheduleState(it, notice.venuesFor(it)) },
                notice.location, notice.benefits, notice.issues, notice.sourceURL, notice.sources, notice.evidence,
                applicationCalendarDraft(notice), notice.schedules.map { phaseCalendarDraft(notice, it) })
        }
    }
    val state: NoticeDetailState? get() = content?.let { it.copy(saved = it.organizationId in favorites.ids) }
}
