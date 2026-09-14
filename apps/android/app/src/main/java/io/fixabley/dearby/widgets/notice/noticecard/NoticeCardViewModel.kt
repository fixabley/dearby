package io.fixabley.dearby.widgets.notice.noticecard

import io.fixabley.dearby.shared.ui.compactPeriodText
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import io.fixabley.dearby.entities.notice.api.NoticeRepository
import io.fixabley.dearby.entities.organization.api.OrganizationRepository
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState

internal class NoticeCardViewModel(private val id: String, private val notices: NoticeRepository,
    private val organizations: OrganizationRepository, private val favorites: FavoritesState) {
    private val content by derivedStateOf {
        notices.revision; organizations.revision
        notices.find(id)?.let { notice ->
            val organization = organizations.find(notice.organizationId)
            val context = notice.contexts.distinctBy { it.organizationId }.mapNotNull { organizations.find(it.organizationId)?.name }.joinToString(" · ")
            NoticeCardState(notice.id, notice.title, listOf(notice.categorySummary, context).filter { it.isNotEmpty() }.joinToString(" · "),
                notice.targetUser, notice.applicationInformation.summary, notice.location.summary, notice.issues.isNotEmpty(), organization?.id, organization?.name,
                applicationDateText = notice.applicationInformation.let { compactPeriodText(null, null, it.closesAt, it.closesOn,
                    it.timezone, it.summary, "마감") })
        }
    }
    val state: NoticeCardState? get() = content?.let { it.copy(saved = it.organizationId in favorites.ids) }
    fun save(): String {
        val value = state
        return if (value?.organizationId != null) {
            favorites.save(value.organizationId)
            "${value.organizationName} 저장됨"
        } else "저장할 조직을 확인 중이에요"
    }
}
