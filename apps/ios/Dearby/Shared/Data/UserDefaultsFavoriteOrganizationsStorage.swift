import Foundation

@MainActor
struct UserDefaultsFavoriteOrganizationsStorage: FavoriteOrganizationsStorage {
    private let defaults: UserDefaults
    private let key = "dearby.favoriteOrganizationIDs.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> Set<String> {
        Set(defaults.stringArray(forKey: key) ?? [])
    }

    func save(_ ids: Set<String>) {
        defaults.set(ids.sorted(), forKey: key)
    }
}
