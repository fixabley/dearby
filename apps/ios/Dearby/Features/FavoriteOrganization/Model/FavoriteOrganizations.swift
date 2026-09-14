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
    func saveOrganization(_ organization: OrganizationModel?) -> SaveOrganizationResult {
        guard let organization else { return .unresolved }
        ids.insert(organization.id)
        repository.save(ids)
        return .saved(organization.name)
    }

    func remove(_ organizationID: String) {
        ids.remove(organizationID)
        repository.save(ids)
    }
}
