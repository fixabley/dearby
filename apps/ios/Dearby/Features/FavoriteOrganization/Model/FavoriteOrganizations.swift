import Observation

@MainActor
@Observable
final class FavoriteOrganizations {
    private(set) var ids: Set<String>
    private let repository: any FavoriteOrganizationsRepository

    init(repository: any FavoriteOrganizationsRepository) {
        self.repository = repository
        ids = repository.load()
    }

    @discardableResult
    func saveOrganization(for notice: Notice, in catalog: NoticeCatalog) -> SaveOrganizationResult {
        guard let organization = catalog.organization(notice.favoriteOrganizationId) else {
            return .unresolved
        }
        ids.insert(organization.id)
        repository.save(ids)
        return .saved(organization)
    }

    func remove(_ organizationID: String) {
        ids.remove(organizationID)
        repository.save(ids)
    }
}
