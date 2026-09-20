import SwiftUI

/// One route is used by Discovery sheets and Favorites navigation.
struct NoticeDestinationView: View {
    let id: String
    let state: AppState
    @State private var detail = NoticeDetailRouteState()

    private var key: NoticeDetailRouteState.Key { .init(id: id, generation: state.generation) }

    var body: some View {
        Group {
            if detail.loadedKey != key {
                ProgressView("공고 불러오는 중")
            } else if let viewModel = detail.viewModel, viewModel.state != nil {
                NoticeDetailPage(viewModel: viewModel, preferences: state.calendarPreferences)
            } else {
                ContentUnavailableView("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
            }
        }
        .task(id: key) { loadDetail() }
    }

    private func loadDetail() {
        detail.load(key, makeViewModel: state.makeDetailViewModel)
    }
}
