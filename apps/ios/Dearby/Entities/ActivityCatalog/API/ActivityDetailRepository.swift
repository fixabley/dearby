import Foundation

/// One App-owned lifetime per catalog session; neither the getter nor UI composition creates a cache.
@MainActor
final class ActivityDetailRepository {
    private(set) var catalog: ActivityCatalog
    private let organizations: OrganizationRepository

    init(catalog: ActivityCatalog, source: (any OrganizationSource)? = nil) {
        self.catalog = catalog
        organizations = OrganizationRepository(source: source ?? SnapshotOrganizationSource(organizations: catalog.organizations))
    }

    func replaceSnapshot(_ catalog: ActivityCatalog, source: (any OrganizationSource)? = nil) {
        self.catalog = catalog
        organizations.replaceSource(source ?? SnapshotOrganizationSource(organizations: catalog.organizations))
    }

    func detail(id: String) -> ActivityDetail? {
        guard let notice = catalog.activities.first(where: { $0.id == id }) else { return nil }
        let path = organizations.path(to: notice.favoriteOrganizationId)
        let contexts = notice.contexts.map {
            ActivityDetailContext(reference: $0, organizationName: organizations.organization($0.organizationId)?.name)
        }
        var relevantIDs = Set(notice.sourceIds)
        relevantIDs.formUnion(notice.evidence.map(\.sourceId))
        let sources = catalog.sources.filter { relevantIDs.contains($0.id) }
        let evidence = notice.evidence.map { reference in
            var resolved = reference
            resolved.sourceURL = sources.first { $0.id == reference.sourceId }.flatMap { URL(string: $0.url) }
            return resolved
        }
        return ActivityDetail(id: notice.id, title: notice.title, aiDescription: notice.summary,
                              descriptionProvenance: "reviewed_sample.summary", organizationID: notice.favoriteOrganizationId,
                              organizationPath: path, organizationLinks: notice.organizationLinks,
                              categoryPath: notice.categoryPath, categorySummary: notice.categorySummary, contexts: contexts,
                              targetUser: notice.audience, participationCondition: notice.eligibility,
                              applicationInformation: notice.application,
                              schedules: notice.schedule.map { phase in
                                  ActivityDetailSchedule(period: phase,
                                      locations: phase.mode == "online" ? [] : notice.location.venues.filter { $0.phase == phase.phase },
                                      locationSummary: notice.location.summary)
                              }, location: notice.location, benefits: notice.benefits, qualityIssues: notice.qualityIssues,
                              edition: notice.edition, sourceURL: catalog.sourceURL(for: notice), sources: sources, evidence: evidence)
    }
}
