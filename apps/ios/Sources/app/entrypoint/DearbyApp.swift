import SwiftUI

@main struct DearbyApp: App {
    @State private var state = CatalogViewModel()
    var body: some Scene {
        WindowGroup {
            NavigationStack { CatalogPage(state: state) }
                .tint(Color(red: 0, green: 0.45, blue: 0.45)).preferredColorScheme(.light)
        }
    }
}
