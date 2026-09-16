import SwiftUI

/// Lazy tab builders pass live models; each connected page observes its own reads.
struct AppTabs<Destination: View>: View {
    let state: AppState
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        TabView {
            Tab("발견", systemImage: "rectangle.stack") {
                NavigationStack {
                    DiscoveryView(snapshotDate: state.snapshotDate, viewModels: state.cards, destination: destination)
                        .toolbar { Button("환경설정", systemImage: "gearshape", action: state.settings.present) }
                }
            }
            Tab("즐겨찾기", systemImage: "heart") {
                NavigationStack {
                    if let favorites = state.favoriteList {
                        FavoriteListView(viewModel: favorites, destination: destination)
                            .toolbar { Button("환경설정", systemImage: "gearshape", action: state.settings.present) }
                    }
                }
            }
        }
    }
}
