import Observation

/// Owns startup, retained storage, retries, and the lifetime of the shared snapshot session.
@MainActor @Observable
final class AppSession {
    private let snapshotReader: any SnapshotReader
    private let favorites: FavoriteOrganizations
    let settings: SettingsViewModel
    let calendarPreferences: CalendarPreferences
    private let makeStorage: () throws -> SwiftDataSnapshotStore
    private var storage: SwiftDataSnapshotStore?
    private(set) var catalog: NoticeSession?
    private(set) var loadFailed = false

    init(snapshotReader: any SnapshotReader,
         favorites: FavoriteOrganizations,
         calendarPreferences: CalendarPreferences,
         makeStorage: @escaping () throws -> SwiftDataSnapshotStore = { try SwiftDataSnapshotStore() }) {
        self.snapshotReader = snapshotReader
        self.favorites = favorites
        self.calendarPreferences = calendarPreferences
        self.settings = SettingsViewModel(preferences: calendarPreferences)
        self.makeStorage = makeStorage
    }

    func loadCatalog() {
        guard catalog == nil else { return }
        do {
            let activeStore: SwiftDataSnapshotStore
            if let storage { activeStore = storage } else {
                activeStore = try makeStorage()
                storage = activeStore
            }
            catalog = try activeStore.makeSession(snapshot: snapshotReader.load(), favorites: favorites)
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}
