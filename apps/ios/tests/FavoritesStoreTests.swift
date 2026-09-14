import Foundation

@main
struct FavoritesStoreTests {
    @MainActor
    static func main() throws {
        let suite = "dearby.test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let catalog = try JSONDecoder().decode(ActivityCatalog.self, from: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1])))
        let careerCards = catalog.feed.filter { ["krc", "db-insurance"].contains($0.favoriteOrganizationId ?? "") }
        precondition(careerCards.count == 2)
        for card in careerCards {
            precondition(card.categorySummary == "채용 › 채용행사")
            precondition(catalog.contextNames(for: card) == "충북대학교")
            precondition(catalog.organizationPath(card.favoriteOrganizationId).count == 1,
                         "The event school must not become the company's parent")
        }
        let store = FavoriteOrganizations(defaults: defaults)
        for card in careerCards { store.save(card.favoriteOrganizationId!) }
        for card in careerCards { store.save(card.favoriteOrganizationId!) }
        precondition(store.ids == ["krc", "db-insurance"], "Repeated saves preserve two distinct subjects")
        precondition(!store.ids.contains("cbnu-career") && !store.ids.contains("cbnu"))
        let reloaded = FavoriteOrganizations(defaults: defaults)
        precondition(reloaded.ids == store.ids, "Favorite must survive store recreation")
        reloaded.remove("krc")
        precondition(FavoriteOrganizations(defaults: defaults).ids == ["db-insurance"])
        reloaded.remove("db-insurance")
        precondition(FavoriteOrganizations(defaults: defaults).ids.isEmpty, "Removal must persist")
        precondition(catalog.feed.count == 4)
        let contest = catalog.feed.first { $0.id == "cbnu-software-1154064" }!
        precondition(contest.favoriteOrganizationId == "yeongnam-cyber-defense")
        precondition(contest.edition == 2)
        precondition(catalog.organizationPath(contest.favoriteOrganizationId).map(\.id)
                     == ["yeongnam-ai-security", "yeongnam-cyber-defense"])
        store.save(contest.favoriteOrganizationId!)
        precondition(store.ids.contains("yeongnam-cyber-defense"))
        precondition(!store.ids.contains("yeongnam-ai-security"))
        print("PASS: catalog decoding, organization deduplication, persistence, removal")
    }
}
