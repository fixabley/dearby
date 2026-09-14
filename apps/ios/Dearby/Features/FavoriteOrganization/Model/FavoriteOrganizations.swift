import Observation

@MainActor
@Observable
final class FavoriteOrganizations {
    private(set) var ids: Set<String>
    private let storage: any FavoriteOrganizationsStorage

    init(storage: any FavoriteOrganizationsStorage) {
        self.storage = storage
        ids = storage.load()
    }

    func save(_ organizationID: String) {
        ids.insert(organizationID)
        storage.save(ids)
    }

    func remove(_ organizationID: String) {
        ids.remove(organizationID)
        storage.save(ids)
    }
}
