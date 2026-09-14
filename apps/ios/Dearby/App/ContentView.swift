import SwiftUI

struct ContentView: View {
    let snapshotReader: any SnapshotReader
    let favorites: FavoriteOrganizations
    @State private var session: NoticeSession?
    @State private var loadFailed = false

    var body: some View {
        // Read shared favorites before lazy Tab builders capture immutable rendering states.
        let _ = favorites.ids
        Group {
            if let session {
                let cards = session.cards.compactMap(\.state)
                let favoriteCards = session.favoriteCards.compactMap(\.state)
                TabView {
                    Tab("발견", systemImage: "rectangle.stack") {
                        NavigationStack {
                            DiscoveryView(snapshotDate: session.snapshotDate, cards: cards, saveOrganization: session.save) { id in
                                destination(id, session: session)
                            }
                        }
                    }
                    Tab("즐겨찾기", systemImage: "heart") {
                        NavigationStack {
                            FavoriteListView(cards: favoriteCards, removeOrganization: favorites.remove) { id in
                                destination(id, session: session)
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

    @ViewBuilder
    private func destination(_ id: String, session: NoticeSession) -> some View {
        if let state = session.detailState(id), let notice = session.notices.notice(id) {
            NoticeDetailDestination(state: state, notice: notice)
        } else {
            ContentUnavailableView("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
        }
    }

    private func loadCatalog() {
        guard session == nil else { return }
        do {
            session = NoticeSession(snapshot: try snapshotReader.load(), favorites: favorites)
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}

#if DEBUG
#Preview {
    ContentView(snapshotReader: BundleSnapshotReader(),
                favorites: FavoriteOrganizations(repository: PreviewFavoritesRepository()))
}

@MainActor
private struct PreviewFavoritesRepository: FavoriteOrganizationsRepository {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
#endif
