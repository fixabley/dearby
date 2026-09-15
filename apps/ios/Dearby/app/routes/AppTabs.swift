import SwiftUI

/// Lazy tab builders pass live models; each connected page observes its own reads.
struct AppTabs<Destination: View>: View {
    let catalog: NoticeSession
    let settings: SettingsViewModel
    @ViewBuilder let destination: (String) -> Destination

    var body: some View {
        TabView {
            Tab("발견", systemImage: "rectangle.stack") {
                NavigationStack {
                    DiscoveryView(snapshotDate: catalog.snapshotDate, viewModels: catalog.cards, destination: destination)
                        .toolbar { Button("환경설정", systemImage: "gearshape", action: settings.present) }
                }
            }
            Tab("즐겨찾기", systemImage: "heart") {
                NavigationStack {
                    FavoriteListView(viewModels: catalog.favoriteCards, destination: destination)
                        .toolbar { Button("환경설정", systemImage: "gearshape", action: settings.present) }
                }
            }
        }
    }
}
