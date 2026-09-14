import SwiftUI

@main
struct DearbyApp: App {
    private let catalogProvider = BundleActivityCatalogProvider()
    @State private var favorites = FavoriteOrganizations(storage: UserDefaultsFavoriteOrganizationsStorage())

    var body: some Scene {
        WindowGroup {
            ContentView(catalogProvider: catalogProvider, favorites: favorites)
        }
    }
}
