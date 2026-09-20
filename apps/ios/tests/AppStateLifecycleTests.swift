// Force casts below assert the bundled JSON fixture schema, never external input.
import Foundation
import SwiftData

extension AppStateTests {
    @MainActor static func testLifecycle(data: Data) throws {
        let snapshot = try lifecycleSnapshot(data: data)
        let reader = MutableAppSnapshot(snapshot: snapshot)
        let probe = OrganizationReadProbe()
        probe.failures = ["detail-only"]
        let favorites = FavoriteOrganizations(repository: LifecycleFavorites())
        var failCommit = false
        var commits = 0
        let storage = try SwiftDataSnapshotStore(inMemory: true, commit: { context in
            commits += 1
            if failCommit { throw LifecycleFailure.commit }
            try context.save()
        })
        let state = AppState(snapshotReader: reader, favorites: favorites,
            calendarPreferences: CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: PreviewBusyCalendarProvider(mode: "empty")),
            makeOrganizationSource: { RecordingOrganizations(snapshot: $0, probe: probe) }, makeStorage: { storage })
        state.loadCatalog()
        precondition(state.isReady && !state.loadFailed && probe.reads["detail-only"] == nil)
        precondition(commits == 1, "Candidate promotions and metadata commit once before publication")
        let id = snapshot.feedIDs[0]
        let originalCard = state.cards.first { $0.id == id }!
        let originalNotices = state.notices!
        let originalOrganizations = state.organizations!
        let originalFavorite = state.favoriteList!.cards[0]
        let originalManifest = try storage.manifest()
        let originalGeneration = state.generation

        var route = NoticeDetailRouteState()
        var creations = 0
        func makeDetail(_ id: String) throws -> NoticeDetailViewModel? {
            creations += 1
            return try state.makeDetailViewModel(id)
        }
        let key = NoticeDetailRouteState.Key(id: id, generation: state.generation)
        precondition(route.loadedKey == nil && route.viewModel == nil && creations == 0)
        route.load(key, makeViewModel: makeDetail)
        precondition(route.loadedKey == key && route.viewModel == nil && creations == 1)
        precondition(probe.reads["detail-only"] == 1 && state.isReady && !state.loadFailed)
        precondition(state.cards.first { $0.id == id } === originalCard)
        // Repeated task/appearance calls and rendering reads do not recreate or retry a failed detail.
        for _ in 0..<10 {
            route.load(key, makeViewModel: makeDetail)
            _ = route.viewModel?.state
        }
        precondition(creations == 1 && probe.reads["detail-only"] == 1)
        probe.failures = []
        let otherID = snapshot.feedIDs[1]
        let otherKey = NoticeDetailRouteState.Key(id: otherID, generation: state.generation)
        route.load(otherKey, makeViewModel: makeDetail)
        precondition(route.viewModel?.state?.id == otherID && creations == 2)
        route.load(key, makeViewModel: makeDetail)
        let originalDetail = route.viewModel!
        precondition(originalDetail.state?.id == id && creations == 3)
        for _ in 0..<10 {
            route.load(key, makeViewModel: makeDetail)
            _ = route.viewModel?.state
        }
        precondition(route.viewModel === originalDetail && creations == 3)
        var reopened = NoticeDetailRouteState()
        reopened.load(key, makeViewModel: makeDetail)
        precondition(reopened.viewModel !== originalDetail && creations == 4)

        let originalPayloads = try storage.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.payload)
        let originalOrgNames = try storage.context.fetch(FetchDescriptor<OrganizationRecord>()).map(\.name)
        let priorCommits = commits
        reader.snapshot = try lifecycleSnapshot(data: data, replacement: true)
        // A VM dependency read fails after the candidate notice has been staged in L2.
        probe.failures = [reader.snapshot.notices.first { $0.id == id }!.favoriteOrganizationId!]
        state.reloadSnapshot()
        precondition(state.loadFailed && state.generation == originalGeneration && commits == priorCommits)
        try assertPreserved()
        probe.failures = []
        failCommit = true
        state.reloadSnapshot()
        precondition(state.loadFailed && commits == priorCommits + 1)
        try assertPreserved()
        failCommit = false
        state.reloadSnapshot()
        precondition(state.isReady && !state.loadFailed && commits == priorCommits + 2)
        precondition(state.generation == originalGeneration + 1)
        precondition(state.notices !== originalNotices && state.organizations !== originalOrganizations)
        precondition(state.cards.first { $0.id == id } !== originalCard && state.favoriteList!.cards[0] !== originalFavorite)
        precondition(state.cards.first { $0.id == id }?.state?.title == "교체된 공고")
        precondition(state.cards.first { $0.id == id }?.state?.organizationName == "교체된 기관")
        route.load(.init(id: id, generation: state.generation), makeViewModel: makeDetail)
        precondition(route.viewModel !== originalDetail && route.viewModel?.state?.title == "교체된 공고")
        precondition(route.viewModel?.state?.organizationName == "교체된 기관" && creations == 5)
        precondition(state.notices?.cachedNotice(id)?.title == "교체된 공고")
        let organization = try state.organizations?.organization(reader.snapshot.notices.first { $0.id == id }!.favoriteOrganizationId)
        precondition(organization?.name == "교체된 기관")
        route.load(.init(id: otherID, generation: state.generation), makeViewModel: makeDetail)
        precondition(route.viewModel?.state == nil && creations == 6)
        precondition(!state.cards.contains { $0.id == otherID } && state.notices?.cachedNotice(otherID) == nil)
        precondition(favorites.ids == ["db-insurance"])

        func assertPreserved() throws {
            precondition(state.isReady && state.generation == originalGeneration)
            precondition(state.notices === originalNotices && state.organizations === originalOrganizations)
            precondition(state.cards.first { $0.id == id } === originalCard && state.favoriteList!.cards[0] === originalFavorite)
            precondition(route.viewModel === originalDetail)
            precondition(originalNotices.cachedNotice(id)?.title == originalCard.state?.title)
            let manifest = try storage.manifest()
            let payloads = try storage.context.fetch(FetchDescriptor<NoticeRecord>()).map(\.payload)
            let names = try storage.context.fetch(FetchDescriptor<OrganizationRecord>()).map(\.name)
            precondition(manifest == originalManifest && Set(payloads) == Set(originalPayloads) && Set(names) == Set(originalOrgNames))
            precondition(!storage.context.hasChanges)
            let reopenedContext = ModelContext(storage.container)
            let diskNotice = try SwiftDataNoticeSource(context: reopenedContext, external: MissingLifecycleNotice()).fetch(id: id)
            precondition(diskNotice?.title == originalCard.state?.title)
        }
        print("PASS: lazy detail no startup query; detail failure isolated; same-key reuse/id+generation replacement/new screen lifetime; candidate VM-read/save failures preserve manifest/L2/L1/display; successful snapshot replaces repositories/cards/favorites/open detail")
    }

}

private func lifecycleSnapshot(data: Data, replacement: Bool = false) throws -> BundleSnapshot {
    let original = try JSONDecoder().decode(BundleSnapshot.self, from: data)
    let id = original.feedIDs[0]
    var raw = try JSONSerialization.jsonObject(with: data) as! [String: Any] // swiftlint:disable:this force_cast
    var notices = raw["activities"] as! [[String: Any]] // swiftlint:disable:this force_cast
    let index = notices.firstIndex { $0["id"] as? String == id }!
    notices[index]["organizationLinks"] = [["organizationId": "detail-only", "role": "contact", "label": "상세 전용"]]
    if replacement {
        notices[index]["title"] = "교체된 공고"
        raw["snapshotAt"] = "2026-10-01T00:00:00+09:00"
        var organizations = raw["organizations"] as! [[String: Any]] // swiftlint:disable:this force_cast
        let orgID = original.notices.first { $0.id == id }!.favoriteOrganizationId!
        let orgIndex = organizations.firstIndex { $0["id"] as? String == orgID }!
        organizations[orgIndex]["name"] = "교체된 기관"
        raw["organizations"] = organizations
        notices.removeAll { $0["id"] as? String == original.feedIDs[1] }
    }
    raw["activities"] = notices
    return try JSONDecoder().decode(BundleSnapshot.self, from: JSONSerialization.data(withJSONObject: raw))
}

private final class MutableAppSnapshot: SnapshotReader {
    var snapshot: BundleSnapshot
    init(snapshot: BundleSnapshot) { self.snapshot = snapshot }
    func load() -> BundleSnapshot { snapshot }
}
@MainActor private final class OrganizationReadProbe {
    var reads: [String: Int] = [:]
    var failures: Set<String> = []
}
@MainActor private struct RecordingOrganizations: OrganizationSource {
    let snapshot: BundleSnapshot
    let probe: OrganizationReadProbe
    func fetch(id: String) throws -> OrganizationModel? {
        probe.reads[id, default: 0] += 1
        if probe.failures.contains(id) { throw LifecycleFailure.read }
        if id == "detail-only" { return OrganizationModel(id: id, name: "상세 기관", parentId: nil) }
        return SnapshotOrganizationSource(organizations: snapshot.organizations).fetch(id: id)
    }
}
@MainActor private struct MissingLifecycleNotice: NoticeRecordSource { func fetch(id: String) -> NoticeModel? { nil } }
@MainActor private struct LifecycleFavorites: FavoriteOrganizationsRepository {
    func load() -> Set<String> { ["db-insurance"] }
    func save(_ ids: Set<String>) {}
}
private enum LifecycleFailure: Error { case read, commit }
