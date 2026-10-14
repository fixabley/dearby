import SwiftUI

struct CatalogPage: View {
    let state: CatalogViewModel
    @Binding var path: [String]
    @State private var showNotice = false
    var body: some View {
        @Bindable var state = state
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                HStack {
                    DearbyLogo(width: 96)
                    Spacer()
                    Button { showNotice = true } label: { Image(systemName: "bell").font(.title2).frame(width: 44, height: 44) }
                        .accessibilityLabel("알림")
                }.padding(.bottom, 8)
                Text("활동 둘러보기").font(.title2.bold())
                DearbySearchField(prompt: "활동 이름이나 설명으로 검색", text: $state.searchText, identifier: "catalog-search")
                    .submitLabel(.search)
                switch state.phase {
                case .loading:
                    ProgressView("활동을 불러오는 중이에요").frame(maxWidth: .infinity).padding(.vertical, 40)
                case .failed:
                    ContentUnavailableView {
                        Label("활동을 불러오지 못했어요", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text("연결을 확인한 뒤 다시 시도해 주세요.")
                    } actions: {
                        Button("다시 시도") { state.reload() }.accessibilityIdentifier("catalog-retry")
                    }
                case .loaded where state.activities.isEmpty:
                    if let query = state.query, !query.isEmpty {
                        ContentUnavailableView("‘\(query)’에 맞는 활동이 없어요", systemImage: "magnifyingglass",
                                               description: Text("다른 검색어로 찾아보세요."))
                    } else {
                        ContentUnavailableView("등록된 활동이 없어요", systemImage: "calendar",
                                               description: Text("새 활동이 공개되면 여기에서 볼 수 있어요."))
                    }
                case .loaded:
                    ForEach(Array(state.activities.enumerated()), id: \.element.id) { index, activity in
                        card(activity)
                            // Near the end of the list: fetch the next page.
                            .onAppear { if index >= state.activities.count - 3 { state.loadMore() } }
                    }
                    if state.loadingMore {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                    } else if state.moreFailed {
                        Button("활동 더 불러오기") { state.loadMore() }.frame(maxWidth: .infinity, minHeight: 44)
                            .accessibilityIdentifier("catalog-more-retry")
                    }
                }
            }.padding(20)
        }
        .scrollDismissesKeyboard(.immediately)
        // Typing waits ~300ms before searching; the first load does not wait.
        .task(id: state.searchText) {
            if state.query != nil {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
            }
            state.search(state.searchText)
        }
        .alert("알림", isPresented: $showNotice) { Button("확인") {} } message: { Text("새로운 알림이 없어요.") }
        .navigationDestination(for: String.self) { id in ActivityDetailView(state: state, activityID: id) }
        .scrollContentBackground(.hidden).background(.white)
        .tint(DearbyStyle.teal).navigationTitle("")
        .navigationBarTitleDisplayMode(.inline).toolbar(.hidden, for: .navigationBar)
    }
    private func card(_ activity: ActivityModel) -> some View {
        ActivityCard(activityID: activity.id, title: activity.title, organization: activity.organization,
            imageURL: activity.imageURL, summary: activity.summary, status: activity.statusLabel(at: state.loadedAt),
            date: activity.dateLabel.isEmpty ? "일정 미확인" : activity.dateLabel,
            applyURL: activity.applicationURL(at: state.loadedAt), recruitmentEnd: activity.recruitmentEnd) {
            path.append(activity.id)
        }
        .environment(\.openURL, OpenURLAction { url in
            state.openedApplication(activity.id)
            return .systemAction(url)
        })
    }
}
