import Foundation

@MainActor final class DiscoveryState {
    let catalog: CatalogState
    static func open() throws -> DiscoveryState {
        try DiscoveryState(store: LocalStore(), api: .configured)
    }
    init(store: LocalStore, api: APIClient) throws {
        catalog = try CatalogState(store: store, api: api)
    }
}
