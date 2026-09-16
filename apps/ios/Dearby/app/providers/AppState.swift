import Observation

/// App state owns the current presentation and shares dependencies assembled in app/providers.
@MainActor @Observable
final class AppState {
    private let snapshotReader: any SnapshotReader
    private let favorites: FavoriteOrganizations
    let settings: SettingsViewModel
    let calendarPreferences: CalendarPreferences
    private let makeOrganizationSource: (BundleSnapshot) -> any OrganizationSource
    private let makeStorage: () throws -> SwiftDataSnapshotStore
    private var storage: SwiftDataSnapshotStore?
    private(set) var notices: NoticeRepository?
    private(set) var organizations: OrganizationRepository?
    private(set) var snapshotDate = ""
    private(set) var generation = 0
    private(set) var cards: [NoticeCardViewModel] = []
    private(set) var favoriteCards: [FavoriteOrganizationCardViewModel] = []
    private(set) var loadFailed = false
    var isReady: Bool { generation > 0 }

    init(snapshotReader: any SnapshotReader,
         favorites: FavoriteOrganizations,
         calendarPreferences: CalendarPreferences,
         makeOrganizationSource: @escaping (BundleSnapshot) -> any OrganizationSource = { SnapshotOrganizationSource(organizations: $0.organizations) },
         makeStorage: @escaping () throws -> SwiftDataSnapshotStore = { try SwiftDataSnapshotStore() }) {
        self.snapshotReader = snapshotReader
        self.favorites = favorites
        self.calendarPreferences = calendarPreferences
        self.settings = SettingsViewModel(preferences: calendarPreferences)
        self.makeStorage = makeStorage
        self.makeOrganizationSource = makeOrganizationSource
    }

    func loadCatalog() {
        guard !isReady else { return }
        reloadSnapshot()
    }

    func reloadSnapshot() {
        do {
            let activeStore: SwiftDataSnapshotStore
            if let storage { activeStore = storage } else {
                activeStore = try makeStorage()
                storage = activeStore
            }
            let snapshot = try snapshotReader.load()
            let candidate = try Self.composeSnapshot(snapshot, storage: activeStore, favorites: favorites,
                organizationSource: makeOrganizationSource(snapshot))
            // No throwing work or suspension after commit: replace caches and presentation together.
            (notices, organizations, cards, favoriteCards, snapshotDate) = candidate
            generation += 1
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }

    /// Called by a route's task, never by body. The route owns the returned model's lifetime.
    func makeDetailViewModel(_ id: String) throws -> NoticeDetailViewModel? {
        guard let notices, let organizations else { return nil }
        return try NoticeDetailViewModel(id: id, notices: notices, organizations: organizations, favorites: favorites)
    }
}
