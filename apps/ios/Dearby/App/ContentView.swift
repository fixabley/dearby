import SwiftUI

struct ContentView: View {
    let catalogProvider: any ActivityCatalogProviding
    let favorites: FavoriteOrganizations
    @State private var catalog: ActivityCatalog?
    @State private var loadFailed = false

    var body: some View {
        // Observe before the lazy tab/navigation builders capture their value inputs.
        let favoriteIDs = favorites.ids
        Group {
            if let catalog {
                TabView {
                    Tab("발견", systemImage: "rectangle.stack") {
                        NavigationStack {
                            DiscoveryView(catalog: catalog, favoriteIDs: favoriteIDs, saveOrganization: favorites.save) { notice in
                                NoticeDetailView(notice: notice, catalog: catalog)
                            }
                        }
                    }
                    Tab("즐겨찾기", systemImage: "heart") {
                        NavigationStack {
                            FavoriteListView(catalog: catalog, favoriteIDs: favoriteIDs, removeOrganization: favorites.remove) { notice in
                                NoticeDetailView(notice: notice, catalog: catalog)
                            }
                        }
                    }
                }
            } else if loadFailed {
                ContentUnavailableView {
                    Label("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
                } description: {
                    Text("앱을 다시 실행해 주세요.")
                } actions: {
                    Button("다시 시도", action: loadCatalog)
                }
            } else {
                ProgressView("공고 불러오는 중")
            }
        }
        .task { loadCatalog() }
    }

    private func loadCatalog() {
        do {
            catalog = try catalogProvider.load()
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}

#if DEBUG
#Preview {
    ContentView(catalogProvider: BundleActivityCatalogProvider(),
                favorites: FavoriteOrganizations(storage: PreviewFavoritesStorage()))
}

@MainActor
private struct PreviewFavoritesStorage: FavoriteOrganizationsStorage {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
#endif
