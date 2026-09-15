// Force casts below assert the bundled JSON fixture schema, never external input.
import Foundation
import Observation
import Synchronization

@main
struct FavoritesStoreTests {
    @MainActor
    static func main() throws {
        try testSnapshotReader()
        let suite = "dearby.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let catalog = try JSONDecoder().decode(BundleSnapshot.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let source = try JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [String: Any] // swiftlint:disable:this force_cast
        let rawNotices = source["activities"] as! [[String: Any]] // swiftlint:disable:this force_cast
        for notice in catalog.notices {
            let raw = rawNotices.first { $0["id"] as? String == notice.id }!
            precondition(notice.targetUser == (raw["audience"] as! [String: Any])["summary"] as! String) // swiftlint:disable:this force_cast
            precondition(notice.participationCondition == (raw["eligibility"] as! [String: Any])["summary"] as! String) // swiftlint:disable:this force_cast
            precondition(notice.applicationInformation.summary == (raw["application"] as! [String: Any])["summary"] as! String) // swiftlint:disable:this force_cast
            precondition(notice.benefits == (raw["benefits"] as! [[String: Any]]).map { $0["summary"] as! String }) // swiftlint:disable:this force_cast
            precondition(notice.qualityIssues == (raw["qualityIssues"] as! [[String: Any]]).map { $0["summary"] as! String }) // swiftlint:disable:this force_cast
        }
        print("PASS: nested canonical summaries preserve all flat app display strings")
        testInMemoryState(catalog: catalog)
        testSaveOrganization(catalog: catalog)

        let careerCards = catalog.feed.filter { ["krc", "db-insurance"].contains($0.favoriteOrganizationId ?? "") }
        precondition(careerCards.count == 2)
        for card in careerCards {
            precondition(card.categorySummary == "채용 › 채용행사")
            precondition(catalog.contextNames(for: card) == "충북대학교")
            precondition(catalog.organizationPath(card.favoriteOrganizationId).count == 1,
                         "The event school must not become the company's parent")
        }
        let store = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(defaults: defaults))
        for card in careerCards { store.saveOrganization(for: card, in: catalog) }
        for card in careerCards { store.saveOrganization(for: card, in: catalog) }
        precondition(store.ids == ["krc", "db-insurance"], "Repeated saves preserve two distinct subjects")
        precondition(!store.ids.contains("cbnu-career") && !store.ids.contains("cbnu"))
        let reloaded = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(defaults: defaults))
        precondition(reloaded.ids == store.ids, "Favorite must survive store recreation")
        reloaded.remove("krc")
        precondition(FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(defaults: defaults)).ids == ["db-insurance"])
        reloaded.remove("db-insurance")
        precondition(FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(defaults: defaults)).ids.isEmpty, "Removal must persist")
        precondition(catalog.feed.count == 4)
        let contest = catalog.feed.first { $0.id == "cbnu-software-1154064" }!
        precondition(contest.favoriteOrganizationId == "yeongnam-cyber-defense")
        precondition(contest.edition == 2)
        precondition(catalog.organizationPath(contest.favoriteOrganizationId).map(\.id)
                     == ["yeongnam-ai-security", "yeongnam-cyber-defense"])
        store.saveOrganization(for: contest, in: catalog)
        precondition(store.ids.contains("yeongnam-cyber-defense"))
        precondition(!store.ids.contains("yeongnam-ai-security"))
        // The legacy array format, including old/unknown IDs, must survive unchanged.
        defaults.set(["cbnu-career", "krc", "krc", "legacy-unknown"], forKey: "dearby.favoriteOrganizationIDs.v1")
        let restored = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(
            defaults: UserDefaults(suiteName: suite)!))
        precondition(restored.ids == ["cbnu-career", "krc", "legacy-unknown"])
        restored.saveOrganization(for: careerCards.first { $0.favoriteOrganizationId == "db-insurance" }!, in: catalog)
        precondition(defaults.stringArray(forKey: "dearby.favoriteOrganizationIDs.v1")
                     == ["cbnu-career", "db-insurance", "krc", "legacy-unknown"])
        restored.remove("krc")
        let recreated = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository(
            defaults: UserDefaults(suiteName: suite)!))
        precondition(recreated.ids == ["cbnu-career", "db-insurance", "legacy-unknown"])
        print("PASS: legacy UserDefaults array compatibility, fresh storage/state restoration")
        print("PASS: catalog decoding, organization deduplication, persistence, removal")
    }

    private static func noticeWithTarget(_ target: String?, from notice: NoticeModel) -> NoticeModel {
        NoticeModel(id: notice.id, title: notice.title, aiDescription: notice.aiDescription,
                       demoVisible: notice.demoVisible, favoriteOrganizationId: target,
                       sourceIds: notice.sourceIds, targetUser: notice.targetUser, participationCondition: notice.participationCondition,
                       applicationInformation: notice.applicationInformation, location: notice.location, schedule: notice.schedule,
                       benefits: notice.benefits, qualityIssues: notice.qualityIssues,
                       categoryPath: notice.categoryPath, contexts: notice.contexts, edition: notice.edition)
    }

    @MainActor
    private static func testSaveOrganization(catalog: BundleSnapshot) {
        let repository = InMemoryFavoritesRepository(ids: ["legacy-unknown"])
        let state = FavoriteOrganizations(repository: repository)
        let notice = catalog.feed.first { $0.favoriteOrganizationId == "krc" }!
        for shouldSave in [false, true] {
            if shouldSave {
                for _ in 0..<2 {
                    guard case .saved(let organization) = state.saveOrganization(for: notice, in: catalog) else {
                        preconditionFailure("Resolved target must succeed")
                    }
                    precondition(organization == catalog.organization("krc")!.name)
                    precondition(organization != notice.title, "Feedback uses canonical organization, not notice title")
                }
                precondition(state.ids == ["legacy-unknown", "krc"])
                precondition(repository.writes.count == 2, "Repeat saves retain synchronous persistence semantics")
            }
            let before = state.ids
            let writeCount = repository.writes.count
            let changes = Mutex(0)
            withObservationTracking { _ = state.ids } onChange: { changes.withLock { $0 += 1 } }
            for target in [nil, "unknown-organization"] as [String?] {
                guard case .unresolved = state.saveOrganization(for: noticeWithTarget(target, from: notice), in: catalog) else {
                    preconditionFailure("Absent and unknown targets must remain unresolved")
                }
                precondition(state.ids == before && repository.ids == before)
                precondition(repository.writes.count == writeCount)
            }
            precondition(changes.withLock { $0 } == 0, "Invalid targets must not mutate observable state")
        }
        print("PASS: validated save result, canonical label, repeat saves, unresolved targets cause no writes or state changes")
    }

    @MainActor
    private static func testInMemoryState(catalog: BundleSnapshot) {
        let storage = InMemoryFavoritesRepository(ids: ["cbnu-career"])
        let state = FavoriteOrganizations(repository: storage)
        // Two consumers of the same root state, like Discovery and Favorites.
        let discoveryIDs = { state.ids }
        let favoritesIDs = { state.ids }
        let discoveryChanges = Mutex(0)
        let favoritesChanges = Mutex(0)
        withObservationTracking { _ = discoveryIDs() } onChange: {
            discoveryChanges.withLock { $0 += 1 }
        }
        withObservationTracking { _ = favoritesIDs() } onChange: {
            favoritesChanges.withLock { $0 += 1 }
        }
        precondition(discoveryIDs() == ["cbnu-career"])
        state.saveOrganization(for: catalog.feed.first { $0.favoriteOrganizationId == "krc" }!, in: catalog)
        precondition(discoveryIDs() == ["cbnu-career", "krc"])
        precondition(favoritesIDs() == discoveryIDs())
        precondition(discoveryChanges.withLock { $0 } == 1)
        precondition(favoritesChanges.withLock { $0 } == 1)
        state.saveOrganization(for: catalog.feed.first { $0.favoriteOrganizationId == "krc" }!, in: catalog)
        state.saveOrganization(for: catalog.feed.first { $0.favoriteOrganizationId == "db-insurance" }!, in: catalog)
        precondition(discoveryIDs() == ["cbnu-career", "krc", "db-insurance"])
        precondition(favoritesIDs() == discoveryIDs())
        withObservationTracking { _ = discoveryIDs() } onChange: {
            discoveryChanges.withLock { $0 += 1 }
        }
        withObservationTracking { _ = favoritesIDs() } onChange: {
            favoritesChanges.withLock { $0 += 1 }
        }
        state.remove("krc")
        precondition(discoveryChanges.withLock { $0 } == 2)
        precondition(favoritesChanges.withLock { $0 } == 2)
        state.remove("not-saved")
        precondition(discoveryIDs() == ["cbnu-career", "db-insurance"])
        precondition(favoritesIDs() == discoveryIDs())
        precondition(storage.ids == state.ids)
        precondition(FavoriteOrganizations(repository: storage).ids == state.ids)
        print("PASS: injected in-memory state, add, duplicate, delete, two Observation consumers")
    }

    private static func testSnapshotReader() throws {
        let sample = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let fixture = URL(fileURLWithPath: "apps/ios/build/CatalogFixture-" + UUID().uuidString + ".bundle")
        try FileManager.default.createDirectory(at: fixture, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: fixture) }
        let resource = fixture.appendingPathComponent("activity-samples.json")
        try sample.write(to: resource)
        let bundle = Bundle(url: fixture)!
        let provider: any SnapshotReader = BundleSnapshotReader(bundle: bundle)
        let catalog = try provider.load()
        precondition(catalog.feed.count == 4)
        let replacement: any SnapshotReader = FixedSnapshotReader(catalog: catalog)
        let replaced = try replacement.load()
        precondition(replaced.feed.map(\.id) == catalog.feed.map(\.id))
        let invalid = String(decoding: sample, as: UTF8.self)
            .replacingOccurrences(of: "reviewed_sample", with: "unsupported")
        try Data(invalid.utf8).write(to: resource)
        do {
            _ = try provider.load()
            preconditionFailure("Unsupported catalog mode must fail")
        } catch {}
        try Data("invalid JSON".utf8).write(to: resource)
        do {
            _ = try provider.load()
            preconditionFailure("Corrupt catalog must fail")
        } catch {}
        print("PASS: injected catalog provider, bundle loading, mode and decoding failures")
    }

}

@MainActor
private final class InMemoryFavoritesRepository: FavoriteOrganizationsRepository {
    var ids: Set<String>
    private(set) var writes: [Set<String>] = []

    init(ids: Set<String> = []) { self.ids = ids }
    func load() -> Set<String> { ids }
    func save(_ ids: Set<String>) { self.ids = ids; writes.append(ids) }
}

private struct FixedSnapshotReader: SnapshotReader {
    let catalog: BundleSnapshot
    func load() throws -> BundleSnapshot { catalog }
}

// Transport fixture helpers for persistence regressions, not production domain composition.
private extension BundleSnapshot {
    var feed: [NoticeModel] { feedIDs.compactMap { id in notices.first { $0.id == id } } }
    func organization(_ id: String?) -> OrganizationModel? { organizations.first { $0.id == id } }
    @MainActor func organizationPath(_ id: String?) -> [OrganizationModel] {
        testValue(try OrganizationRepository(source: SnapshotOrganizationSource(organizations: organizations)).path(to: id))
    }
    func contextNames(for notice: NoticeModel) -> String {
        var seen: Set<String> = []
        return notice.contexts.compactMap { seen.insert($0.organizationId).inserted ? organization($0.organizationId)?.name : nil }.joined(separator: " · ")
    }
}
private extension FavoriteOrganizations {
    @discardableResult
    func saveOrganization(for notice: NoticeModel, in snapshot: BundleSnapshot) -> SaveOrganizationResult {
        saveOrganization(snapshot.organization(notice.favoriteOrganizationId))
    }
}

private func testValue<T>(_ operation: @autoclosure () throws -> T) -> T {
    do { return try operation() } catch { preconditionFailure("Unexpected error: \(error)") }
}
