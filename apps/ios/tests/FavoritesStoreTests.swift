import Foundation
import Observation
import Synchronization

@main
struct FavoritesStoreTests {
    @MainActor
    static func main() throws {
        try testCatalogRepository()
        let suite = "dearby.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let catalog = try JSONDecoder().decode(ActivityCatalog.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        testInMemoryState(catalog: catalog)
        testSaveOrganization(catalog: catalog)
        try testNoticeSummary(catalog: catalog)
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

    private static func noticeWithTarget(_ target: String?, from notice: ActivityNotice) -> ActivityNotice {
        ActivityNotice(id: notice.id, title: notice.title, summary: notice.summary,
                       demoVisible: notice.demoVisible, favoriteOrganizationId: target,
                       sourceIds: notice.sourceIds, audience: notice.audience, eligibility: notice.eligibility,
                       application: notice.application, location: notice.location, schedule: notice.schedule,
                       benefits: notice.benefits, qualityIssues: notice.qualityIssues,
                       categoryPath: notice.categoryPath, contexts: notice.contexts, edition: notice.edition)
    }

    private static func testNoticeSummary(catalog: ActivityCatalog) throws {
        let notice = catalog.activities.first { $0.favoriteOrganizationId == "krc" }!
        let summary = catalog.summary(for: notice)
        precondition(summary.notice.id == notice.id && summary.notice.title == notice.title)
        precondition(summary.organization?.id == "krc")
        precondition(summary.organization?.name == catalog.organization("krc")?.name)
        precondition(summary.contextNames == "충북대학교")
        for target in [nil, "unknown-organization"] as [String?] {
            let unresolved = catalog.summary(for: noticeWithTarget(target, from: notice))
            precondition(unresolved.organization == nil)
            precondition(unresolved.notice.id == notice.id)
            precondition(unresolved.contextNames == "충북대학교", "Unresolved target must not discard event context")
        }
        let contest = catalog.activities.first { $0.favoriteOrganizationId == "yeongnam-cyber-defense" }!
        let contestSummary = catalog.summary(for: contest)
        precondition(contestSummary.organization?.id == "yeongnam-cyber-defense")
        precondition(contestSummary.notice.edition == 2 && contestSummary.contextNames.isEmpty)
        print("PASS: notice summary resolved/unresolved target, school context, contest edition")
    }

    @MainActor
    private static func testSaveOrganization(catalog: ActivityCatalog) {
        let repository = InMemoryFavoritesRepository(ids: ["legacy-unknown"])
        let state = FavoriteOrganizations(repository: repository)
        let notice = catalog.feed.first { $0.favoriteOrganizationId == "krc" }!
        for shouldSave in [false, true] {
            if shouldSave {
                for _ in 0..<2 {
                    guard case .saved(let organization) = state.saveOrganization(for: notice, in: catalog) else {
                        preconditionFailure("Resolved target must succeed")
                    }
                    precondition(organization.id == "krc")
                    precondition(organization.name == catalog.organization("krc")!.name)
                    precondition(organization.name != notice.title, "Feedback uses canonical organization, not notice title")
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
    private static func testInMemoryState(catalog: ActivityCatalog) {
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

    private static func testCatalogRepository() throws {
        let sample = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let fixture = URL(fileURLWithPath: "apps/ios/build/CatalogFixture-" + UUID().uuidString + ".bundle")
        try FileManager.default.createDirectory(at: fixture, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: fixture) }
        let resource = fixture.appendingPathComponent("activity-samples.json")
        try sample.write(to: resource)
        let bundle = Bundle(url: fixture)!
        let provider: any ActivityCatalogRepository = BundleActivityCatalogRepository(bundle: bundle)
        let catalog = try provider.load()
        precondition(catalog.feed.count == 4)
        let replacement: any ActivityCatalogRepository = FixedCatalogRepository(catalog: catalog)
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

private struct FixedCatalogRepository: ActivityCatalogRepository {
    let catalog: ActivityCatalog
    func load() throws -> ActivityCatalog { catalog }
}
