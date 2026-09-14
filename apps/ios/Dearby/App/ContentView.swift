import SwiftUI

struct ContentView: View {
    let catalogRepository: any ActivityCatalogRepository
    let favorites: FavoriteOrganizations
    @State private var details: ActivityDetailRepository?
    @State private var loadFailed = false

    var body: some View {
        // Observe before the lazy tab/navigation builders capture their value inputs.
        let favoriteIDs = favorites.ids
        Group {
            if let details {
                let catalog = details.catalog
                TabView {
                    Tab("발견", systemImage: "rectangle.stack") {
                        NavigationStack {
                            DiscoveryView(catalog: catalog, favoriteIDs: favoriteIDs, saveOrganization: { notice in
                                favorites.saveOrganization(for: notice, in: catalog)
                            }) { notice in
                                if let detail = details.detail(id: notice.id) {
                                    NoticeDetailDestination(detail: detail)
                                }
                            }
                        }
                    }
                    Tab("즐겨찾기", systemImage: "heart") {
                        NavigationStack {
                            FavoriteListView(catalog: catalog, favoriteIDs: favoriteIDs, removeOrganization: favorites.remove) { notice in
                                if let detail = details.detail(id: notice.id) {
                                    NoticeDetailDestination(detail: detail)
                                }
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
        guard details == nil else { return }
        do {
            details = ActivityDetailRepository(catalog: try catalogRepository.load())
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}

#if DEBUG
#Preview {
    ContentView(catalogRepository: BundleActivityCatalogRepository(),
                favorites: FavoriteOrganizations(repository: PreviewFavoritesRepository()))
}

@MainActor
private struct PreviewFavoritesRepository: FavoriteOrganizationsRepository {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
#endif
