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
        let links = notice.organizationLinks.map {
            ActivityDetailContext(reference: $0, organizationName: organizations.organization($0.organizationId)?.name)
        }
        var relevantIDs = Set(notice.sourceIds)
        relevantIDs.formUnion(notice.evidence.map(\.sourceId))
        let sources = catalog.sources.filter { relevantIDs.contains($0.id) }
        return ActivityDetail(notice: notice, organizationPath: path, contexts: contexts,
                              organizationLinks: links, sources: sources)
    }
}
