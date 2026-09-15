import SwiftUI

struct ContentView: View {
    let snapshotReader: any SnapshotReader
    let favorites: FavoriteOrganizations
    let calendarPreferences: CalendarPreferences
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @State private var mapFailed = false
    @State private var showSettings = false
    var makeStorage: () throws -> SwiftDataSnapshotStore = { try SwiftDataSnapshotStore() }
    @State private var storage: SwiftDataSnapshotStore?
    @State private var session: NoticeSession?
    @State private var loadFailed = false

    var body: some View {
        // Read shared favorites before lazy Tab builders capture immutable rendering states.
        let _ = favorites.ids
        Group {
            if let session {
                let favoriteCards = session.favoriteCards.compactMap(\.state)
                TabView {
                    Tab("발견", systemImage: "rectangle.stack") {
                        NavigationStack {
                            DiscoveryView(snapshotDate: session.snapshotDate, viewModels: session.cards) { id in
                                NoticeDestinationView(id: id, session: session, calendarPreferences: calendarPreferences)
                            } onOpenMap: { id, scheduleIndex, venueIndex in
                                guard let notice = session.notices.cachedNotice(id),
                                      notice.schedules.indices.contains(scheduleIndex),
                                      notice.schedules[scheduleIndex].locations.indices.contains(venueIndex) else { return }
                                VenueMapLauncher.open(notice.schedules[scheduleIndex].locations[venueIndex],
                                    using: { url, completion in openURL(url, completion: completion) },
                                    onFailure: { mapFailed = true })
                            }
                            .toolbar { Button("환경설정", systemImage: "gearshape") { showSettings = true } }
                        }
                    }
                    Tab("즐겨찾기", systemImage: "heart") {
                        NavigationStack {
                            FavoriteListView(cards: favoriteCards, removeOrganization: favorites.remove) { id in
                                NoticeDestinationView(id: id, session: session, calendarPreferences: calendarPreferences)
                            }
                            .toolbar { Button("환경설정", systemImage: "gearshape") { showSettings = true } }
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
        .alert("지도 열기 실패", isPresented: $mapFailed) {
            Button("확인", role: .cancel) {}
        } message: {
            Text("지도를 열지 못했어요.")
        }
        .task { loadCatalog() }
        .task(id: session != nil || loadFailed) {
            guard session != nil || loadFailed else { return }
            await Task.yield()
            calendarPreferences.start()
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                SettingsView(connection: calendarPreferences.connection, isEnabled: calendarPreferences.switchIsOn,
                    onToggle: calendarPreferences.setEnabled,
                    onSettings: { openURL(URL(string: UIApplication.openSettingsURLString)!) })
                    .toolbar { Button("완료") { showSettings = false } }
            }
        }
        .alert("겹치는 일정 확인하기", isPresented: Binding(get: { calendarPreferences.showFirstPrompt }, set: { _ in })) {
            Button("나중에", role: .cancel, action: calendarPreferences.later)
            Button("켜기", action: calendarPreferences.enableFromFirstPrompt)
        } message: {
            Text("캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.")
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: calendarPreferences.lifecycle(.active)
            case .background: calendarPreferences.lifecycle(.background)
            default: calendarPreferences.lifecycle(.inactive)
            }
        }
    }

    private func loadCatalog() {
        guard session == nil else { return }
        do {
            let activeStore: SwiftDataSnapshotStore
            if let storage { activeStore = storage } else {
                activeStore = try makeStorage()
                storage = activeStore
            }
            session = try activeStore.makeSession(snapshot: snapshotReader.load(), favorites: favorites)
            loadFailed = false
        } catch {
            loadFailed = true
        }
    }
}

#if DEBUG
#Preview {
    ContentView(snapshotReader: BundleSnapshotReader(),
                favorites: FavoriteOrganizations(repository: PreviewFavoritesRepository()),
                calendarPreferences: CalendarPreferences(store: MemoryCalendarPreferenceStore(), provider: PreviewBusyCalendarProvider(mode: "empty")),
                makeStorage: { try SwiftDataSnapshotStore(inMemory: true) })
}

@MainActor
private struct PreviewFavoritesRepository: FavoriteOrganizationsRepository {
    func load() -> Set<String> { [] }
    func save(_ ids: Set<String>) {}
}
#endif
