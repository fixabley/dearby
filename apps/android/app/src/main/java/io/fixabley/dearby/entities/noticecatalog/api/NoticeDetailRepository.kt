package io.fixabley.dearby.entities.noticecatalog.api

import io.fixabley.dearby.entities.noticecatalog.model.*

/** App-owned lifetime: initialized outside Compose, one empty organization cache per instance. */
internal class NoticeDetailRepository(
    private val provider: CatalogProvider,
    private val organizationSource: (List<Organization>) -> OrganizationSource = ::InMemoryOrganizationSource,
) : CatalogProvider {
    private var snapshot: NoticeCatalog? = null
    private val organizations = OrganizationRepository(InMemoryOrganizationSource(emptyList()))

    @Synchronized override fun load(): NoticeCatalog = provider.load().also {
        if (snapshot !== it) replaceSnapshot(it)
    }

    @Synchronized fun replaceSnapshot(replacement: NoticeCatalog) {
        organizations.replaceSource(organizationSource(replacement.organizations))
        snapshot = replacement
    }

    @Synchronized fun detail(id: String): NoticeDetail? {
        val notice = snapshot?.feed?.firstOrNull { it.id == id } ?: return null
        fun resolve(reference: NoticeContext) = ResolvedOrganizationRole(reference.organizationId,
            reference.role, reference.label, organizations.find(reference.organizationId)?.name)
        return NoticeDetail(notice.id, notice.title, notice.summary, "reviewed_sample_summary",
            notice.organizationId, organizations.path(notice.organizationId),
            notice.organizationLinks.map(::resolve), notice.categoryPath, notice.categorySummary,
            notice.contexts.map(::resolve), notice.edition, notice.audience, notice.eligibility,
            notice.application, notice.schedule.map { phase ->
                NoticeScheduleDetail(phase, if (phase.mode == "online") emptyList()
                    else notice.location.venues.filter { it.phase == phase.phase })
            }, notice.location, notice.benefits, notice.issues, notice.sourceUrl, notice.sources, notice.evidence)
    }
}
