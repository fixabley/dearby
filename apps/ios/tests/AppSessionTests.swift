import Foundation
import Observation
import Synchronization

@main
struct AppSessionTests {
    @MainActor static func main() throws {
        let snapshot = try JSONDecoder().decode(BundleSnapshot.self,
            from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let reader = RetryingSnapshotReader(snapshot: snapshot)
        let favorites = FavoriteOrganizations(repository: StartupFavorites())
        let preferences = CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: PreviewBusyCalendarProvider(mode: "empty"))
        var creations = 0
        let session = AppSession(snapshotReader: reader, favorites: favorites, calendarPreferences: preferences) {
            creations += 1
            if creations == 1 { throw CocoaError(.fileReadUnknown) }
            return try SwiftDataSnapshotStore(inMemory: true)
        }
        precondition(session.catalog == nil && !session.loadFailed && !preferences.showFirstPrompt)
        session.loadCatalog()
        precondition(session.loadFailed && session.catalog == nil && creations == 1 && reader.reads == 0)
        session.loadCatalog()
        precondition(session.loadFailed && session.catalog == nil && creations == 2 && reader.reads == 1)
        session.loadCatalog()
        let catalog = session.catalog!
        precondition(!session.loadFailed && creations == 2 && reader.reads == 2)
        session.loadCatalog()
        precondition(session.catalog === catalog && creations == 2 && reader.reads == 2)
        precondition(session.settings.preferences === preferences)
        let card = catalog.cards.first { $0.state?.organizationName != nil }!
        let detail = catalog.detailViewModel(card.id)!
        let discoveryChanges = Mutex(0)
        let favoritesChanges = Mutex(0)
        let detailChanges = Mutex(0)
        withObservationTracking { _ = card.state?.saved } onChange: { discoveryChanges.withLock { $0 += 1 } }
        withObservationTracking { _ = catalog.favoriteCards.filter { $0.state != nil } } onChange: { favoritesChanges.withLock { $0 += 1 } }
        withObservationTracking { _ = detail.state?.saved } onChange: { detailChanges.withLock { $0 += 1 } }
        _ = card.save()
        precondition(discoveryChanges.withLock { $0 } == 1 && favoritesChanges.withLock { $0 } == 1 && detailChanges.withLock { $0 } == 1)
        precondition(card.state?.saved == true && detail.state?.saved == true)
        let savedCard = catalog.favoriteCards.first { $0.state != nil }!
        withObservationTracking { _ = card.state?.saved } onChange: { discoveryChanges.withLock { $0 += 1 } }
        withObservationTracking { _ = catalog.favoriteCards.filter { $0.state != nil } } onChange: { favoritesChanges.withLock { $0 += 1 } }
        withObservationTracking { _ = detail.state?.saved } onChange: { detailChanges.withLock { $0 += 1 } }
        savedCard.remove()
        precondition(discoveryChanges.withLock { $0 } == 2 && favoritesChanges.withLock { $0 } == 2 && detailChanges.withLock { $0 } == 2)
        precondition(card.state?.saved == false && detail.state?.saved == false && savedCard.state == nil)
        precondition(card.venue(scheduleIndex: -1, venueIndex: 0) == nil)
        precondition(card.venue(scheduleIndex: 0, venueIndex: -1) == nil)
        precondition(card.venue(scheduleIndex: 999, venueIndex: 0) == nil)
        print("PASS: startup storage failure/retry, source failure retains storage, success idempotent; independent discovery/favorites/open-detail Observation save+remove; invalid map indices")
    }
}

private final class RetryingSnapshotReader: SnapshotReader {
    let snapshot: BundleSnapshot
    var reads = 0
    init(snapshot: BundleSnapshot) { self.snapshot = snapshot }
    func load() throws -> BundleSnapshot {
        reads += 1
        if reads == 1 { throw CocoaError(.fileReadCorruptFile) }
        return snapshot
    }
}

@MainActor private struct StartupFavorites: FavoriteOrganizationsRepository {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
