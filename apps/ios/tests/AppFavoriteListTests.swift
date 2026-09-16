// Force casts assert only the checked-in JSON fixture shape.
import Foundation
import Observation
import Synchronization

extension AppStateTests {
    @MainActor static func testFavoriteList(data: Data) throws {
        var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any] // swiftlint:disable:this force_cast
        var organizations = raw["organizations"] as! [[String: Any]] // swiftlint:disable:this force_cast
        organizations.append(["id": "unused-broken", "name": "Unused", "parentOrganizationId": "broken-parent"])
        organizations.append(["id": "broken-parent", "name": "Parent"])
        raw["organizations"] = organizations
        let snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        let probe = FavoriteListReadProbe(snapshot: snapshot)
        probe.failures = ["unused-broken", "broken-parent"]
        let favorites = FavoriteOrganizations(repository: EmptyListFavorites())
        let storage = try SwiftDataSnapshotStore(inMemory: true)
        let reader = FavoriteListSnapshot(snapshot: snapshot)
        let app = AppState(snapshotReader: reader, favorites: favorites,
            calendarPreferences: CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: PreviewBusyCalendarProvider(mode: "empty")),
            makeOrganizationSource: { _ in probe }, makeStorage: { storage })
        app.loadCatalog()
        precondition(app.isReady && !app.loadFailed, "An unsaved organization read/path failure must not fail the feed")
        precondition(probe.reads["unused-broken"] == nil && probe.reads["broken-parent"] == nil)
        let list = app.favoriteList!
        precondition(list.cards.isEmpty)
        var expected: [String: [String]] = [:]
        for id in snapshot.feedIDs {
            if let orgID = snapshot.notices.first(where: { $0.id == id })?.favoriteOrganizationId {
                expected[orgID, default: []].append(id)
            }
        }
        precondition(list.noticeIDsByOrganization == expected)
        list.startObserving() // Repeated activation must not add another subscription.
        let listChanges = Mutex(0)
        withObservationTracking { _ = list.cards } onChange: { listChanges.withLock { $0 += 1 } }
        let card = app.cards.first { $0.state?.organizationName != nil }!
        let detail = try app.makeDetailViewModel(card.id)!
        _ = card.save()
        precondition(listChanges.withLock { $0 } == 1 && list.cards.count == 1)
        let saved = list.cards[0]
        precondition(saved.state!.notices.map(\.id) == expected[saved.id])
        precondition(card.state!.saved && detail.state!.saved)
        let reads = probe.reads
        for _ in 0..<10 { _ = list.cards.compactMap(\.state); _ = list.loadFailed }
        _ = card.save()
        precondition(probe.reads == reads && list.cards[0] === saved, "Body reads and duplicate saves do no source work")
        saved.remove()
        precondition(list.cards.isEmpty && !card.state!.saved && !detail.state!.saved)
        _ = card.save()
        precondition(list.cards.count == 1 && card.state!.saved && detail.state!.saved)

        // A newly saved path can fail without undoing the user's saved-ID action or losing existing cards.
        let missing = OrganizationModel(id: "unused-broken", name: "Unused", parentId: "broken-parent")
        favorites.saveOrganization(missing)
        precondition(list.loadFailed && list.cards.count == 1 && favorites.ids.contains(missing.id))
        probe.failures = ["broken-parent"]
        list.reload()
        precondition(list.loadFailed && list.cards.count == 1)
        probe.failures = []
        list.reload()
        precondition(!list.loadFailed && list.cards.map(\.id).contains(missing.id))
        let emptyOrganization = list.cards.first { $0.id == missing.id }!
        precondition(emptyOrganization.state!.notices.isEmpty)
        let previousManifest = try storage.manifest()
        let previousGeneration = app.generation
        raw["snapshotAt"] = "2026-11-01T00:00:00+09:00"
        reader.snapshot = try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
        probe.failures = ["broken-parent"]
        app.reloadSnapshot()
        let afterFailureManifest = try storage.manifest()
        precondition(app.loadFailed && app.favoriteList === list && app.generation == previousGeneration)
        precondition(previousManifest == afterFailureManifest)
        emptyOrganization.remove() // Failed candidate did not steal/detach the previous subscriber.
        precondition(!list.cards.contains { $0.id == missing.id })
        probe.failures = []

        // No old snapshot subscription survives a successful replacement, even if its view retains the list.
        app.reloadSnapshot()
        let replacement = app.favoriteList!
        precondition(replacement !== list)
        let oldCardIDs = list.cards.map(\.id)
        favorites.remove(saved.id)
        precondition(replacement.cards.isEmpty && list.cards.map(\.id) == oldCardIDs)
        weak var released: FavoriteOrganizationListViewModel?
        do {
            let temporary = try FavoriteOrganizationListViewModel(organizationIDs: snapshot.organizations.map(\.id),
                feedIDs: snapshot.feedIDs, notices: app.notices!, organizations: app.organizations!, favorites: favorites)
            temporary.startObserving(); released = temporary
        }
        precondition(released == nil, "Favorites must not retain the snapshot/list subscriber")
        var notifications = 0
        var isHandling = false
        let subscription = favorites.observeChanges {
            precondition(!isHandling, "Post-mutation observers must not reenter")
            isHandling = true
            notifications += 1
            if favorites.ids.contains(missing.id) { favorites.remove(missing.id) }
            isHandling = false
        }
        favorites.saveOrganization(missing)
        precondition(notifications == 2 && replacement.cards.isEmpty && !favorites.ids.contains(missing.id))
        favorites.remove(missing.id)
        precondition(notifications == 2, "No change must not notify twice")
        withExtendedLifetime(subscription) {}
        print("PASS saved-list: unsaved read/path failures isolated, one organization index, synchronous add/remove/detail agreement, no body/duplicate-save reads, explicit retry, empty linked notices, old subscription detached, weak lifetime")
    }
}

private final class FavoriteListSnapshot: SnapshotReader {
    var snapshot: BundleSnapshot
    init(snapshot: BundleSnapshot) { self.snapshot = snapshot }
    func load() -> BundleSnapshot { snapshot }
}
@MainActor private struct EmptyListFavorites: FavoriteOrganizationsRepository {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
@MainActor private final class FavoriteListReadProbe: OrganizationSource {
    let snapshot: BundleSnapshot
    var failures: Set<String> = []
    var reads: [String: Int] = [:]
    init(snapshot: BundleSnapshot) { self.snapshot = snapshot }
    func fetch(id: String) throws -> OrganizationModel? {
        reads[id, default: 0] += 1
        if failures.contains(id) { throw CocoaError(.fileReadUnknown) }
        return snapshot.organizations.first { $0.id == id }
    }
}
