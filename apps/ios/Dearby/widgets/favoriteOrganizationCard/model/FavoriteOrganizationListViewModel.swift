import Observation

/// Owns the visible saved-organization list for one snapshot, not the saved-ID source of truth.
@MainActor @Observable
final class FavoriteOrganizationListViewModel {
    private(set) var cards: [FavoriteOrganizationCardViewModel] = []
    private(set) var loadFailed = false
    let noticeIDsByOrganization: [String: [String]]
    private let order: [String: Int]
    private let notices: NoticeRepository
    private let organizations: OrganizationRepository
    private let favorites: FavoriteOrganizations
    private var observation: FavoriteOrganizationsObservation?

    init(organizationIDs: [String], feedIDs: [String], notices: NoticeRepository,
         organizations: OrganizationRepository, favorites: FavoriteOrganizations) throws {
        self.notices = notices; self.organizations = organizations; self.favorites = favorites
        order = Dictionary(uniqueKeysWithValues: organizationIDs.enumerated().map { ($0.element, $0.offset) })
        var index: [String: [String]] = [:]
        for id in feedIDs {
            if let organizationID = try notices.notice(id)?.favoriteOrganizationId {
                index[organizationID, default: []].append(id)
            }
        }
        noticeIDsByOrganization = index
        // Candidate list failures still abort the enclosing snapshot transaction.
        cards = try makeCards(reusing: [:])
    }

    /// App activates only after the snapshot's final save, and stops the previous snapshot's subscription.
    func startObserving() {
        guard observation == nil else { return }
        observation = favorites.observeChanges { [weak self] in self?.reload() }
    }
    func stopObserving() { observation = nil }

    /// Explicit action, never a body/computed-getter I/O path. Existing cards remain on a read failure.
    func reload() {
        let retained = cards.filter { favorites.ids.contains($0.id) }
        do {
            cards = try makeCards(reusing: Dictionary(uniqueKeysWithValues: retained.map { ($0.id, $0) }))
            loadFailed = false
        } catch {
            cards = retained
            loadFailed = true
        }
    }

    private func makeCards(reusing existing: [String: FavoriteOrganizationCardViewModel]) throws -> [FavoriteOrganizationCardViewModel] {
        try favorites.ids.filter { order[$0] != nil }.sorted { order[$0]! < order[$1]! }.compactMap { id in
            let card = try existing[id] ?? FavoriteOrganizationCardViewModel(id: id,
                noticeIDs: noticeIDsByOrganization[id] ?? [], notices: notices, organizations: organizations, favorites: favorites)
            return card.state == nil ? nil : card
        }
    }
}
