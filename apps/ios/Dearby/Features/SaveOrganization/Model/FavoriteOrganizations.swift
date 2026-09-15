/// Save/remove action facade. The one observable ID owner lives in Entities/Favorite.
@MainActor
final class FavoriteOrganizations {
    private let favoriteStore: FavoriteOrganizationStore
    var ids: Set<String> { favoriteStore.ids }

    init(repository: any FavoriteOrganizationsRepository) {
        favoriteStore = FavoriteOrganizationStore(repository: repository)
    }

    @discardableResult
    func saveOrganization(_ organization: OrganizationModel?) -> SaveOrganizationResult {
        guard let organization else { return .unresolved }
        favoriteStore.insert(organization.id)
        return .saved(organization.name)
    }

    func remove(_ organizationID: String) { favoriteStore.remove(organizationID) }
}
