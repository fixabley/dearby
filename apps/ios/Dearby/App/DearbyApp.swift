import SwiftUI

@main
struct DearbyApp: App {
    private let catalogRepository = BundleActivityCatalogRepository()
    @State private var favorites = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository())

    var body: some Scene {
        WindowGroup {
            ContentView(catalogRepository: catalogRepository, favorites: favorites)
        }
    }
}
