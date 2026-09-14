import Foundation

/// One App-owned lifetime per catalog session; neither the getter nor UI composition creates a cache.
@MainActor
final class NoticeDetailRepository {
    private(set) var catalog: NoticeCatalog
    private let organizations: OrganizationRepository

    init(catalog: NoticeCatalog, source: (any OrganizationSource)? = nil) {
        self.catalog = catalog
        organizations = OrganizationRepository(source: source ?? SnapshotOrganizationSource(organizations: catalog.organizations))
    }

    func replaceSnapshot(_ catalog: NoticeCatalog, source: (any OrganizationSource)? = nil) {
        self.catalog = catalog
        organizations.replaceSource(source ?? SnapshotOrganizationSource(organizations: catalog.organizations))
    }

    func detail(id: String) -> NoticeDetail? {
        guard let notice = catalog.notices.first(where: { $0.id == id }) else { return nil }
        let path = organizations.path(to: notice.favoriteOrganizationId)
        let contexts = notice.contexts.map {
            NoticeDetailContext(reference: $0, organizationName: organizations.organization($0.organizationId)?.name)
        }
        let links = notice.organizationLinks.map {
            NoticeDetailContext(reference: $0, organizationName: organizations.organization($0.organizationId)?.name)
        }
        var relevantIDs = Set(notice.sourceIds)
        relevantIDs.formUnion(notice.evidence.map(\.sourceId))
        let sources = catalog.sources.filter { relevantIDs.contains($0.id) }
        return NoticeDetail(notice: notice, organizationPath: path, contexts: contexts,
                              organizationLinks: links, sources: sources)
    }
}
