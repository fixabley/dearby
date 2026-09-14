/// Persistence boundary. The observable state owns the in-session source of truth.
@MainActor
protocol FavoriteOrganizationsRepository {
    func load() -> Set<String>
    func save(_ ids: Set<String>)
}
