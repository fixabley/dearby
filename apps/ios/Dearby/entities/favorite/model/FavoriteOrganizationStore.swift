import Observation

/// The single observable source of saved IDs; it does not resolve Organization models.
@MainActor
@Observable
final class FavoriteOrganizationStore {
    private(set) var ids: Set<String>
    private let repository: any FavoriteOrganizationsRepository

    init(repository: any FavoriteOrganizationsRepository) {
        self.repository = repository
        ids = repository.load()
    }

    func insert(_ id: String) {
        ids.insert(id)
        repository.save(ids)
    }

    func remove(_ id: String) {
        ids.remove(id)
        repository.save(ids)
    }
}
