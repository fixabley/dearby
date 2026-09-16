/// Save/remove action facade. The one observable ID owner lives in Entities/Favorite.
@MainActor
final class FavoriteOrganizations {
    private struct Observer { weak var subscription: FavoriteOrganizationsObservation? }
    private var observers: [Observer] = []
    private var isNotifying = false
    private var hasPendingChange = false
    private let favoriteStore: FavoriteOrganizationStore
    var ids: Set<String> { favoriteStore.ids }

    init(repository: any FavoriteOrganizationsRepository) {
        favoriteStore = FavoriteOrganizationStore(repository: repository)
    }

    @discardableResult
    func saveOrganization(_ organization: OrganizationModel?) -> SaveOrganizationResult {
        guard let organization else { return .unresolved }
        let changed = !ids.contains(organization.id)
        favoriteStore.insert(organization.id)
        if changed { notify() }
        return .saved(organization.name)
    }

    func remove(_ organizationID: String) {
        let changed = ids.contains(organizationID)
        favoriteStore.remove(organizationID)
        if changed { notify() }
    }

    /// Synchronous post-mutation notification; subscribers keep the lifetime token, never an ID copy.
    func observeChanges(_ onChange: @escaping @MainActor () -> Void) -> FavoriteOrganizationsObservation {
        observers.removeAll { $0.subscription == nil }
        let subscription = FavoriteOrganizationsObservation(onChange: onChange)
        observers.append(Observer(subscription: subscription))
        return subscription
    }

    private func notify() {
        hasPendingChange = true
        guard !isNotifying else { return }
        isNotifying = true
        defer { isNotifying = false }
        while hasPendingChange {
            hasPendingChange = false
            observers.removeAll { $0.subscription == nil }
            for observer in observers { observer.subscription?.onChange() }
        }
    }
}
