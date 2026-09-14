import SwiftUI

@main
struct DearbyApp: App {
    private let snapshotReader = BundleSnapshotReader()
    @State private var favorites = FavoriteOrganizations(repository: UserDefaultsFavoriteOrganizationsRepository())

    var body: some Scene {
        WindowGroup {
            ContentView(snapshotReader: snapshotReader, favorites: favorites)
        }
    }
}
