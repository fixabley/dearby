import Foundation
import Observation

@MainActor
@Observable
final class FavoriteOrganizations {
    private(set) var ids: Set<String>
    private let defaults: UserDefaults
    private let key = "dearby.favoriteOrganizationIDs.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        ids = Set(defaults.stringArray(forKey: key) ?? [])
    }

    func save(_ organizationID: String) {
        ids.insert(organizationID)
        persist()
    }

    func remove(_ organizationID: String) {
        ids.remove(organizationID)
        persist()
    }

    private func persist() {
        defaults.set(ids.sorted(), forKey: key)
    }
}
