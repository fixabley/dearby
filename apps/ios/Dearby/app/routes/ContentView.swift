import SwiftUI

struct ContentView: View {
    let state: AppState

    var body: some View {
        SettingsPresentation(viewModel: state.settings, startupFinished: state.isReady || state.loadFailed) {
            Group {
                if state.isReady {
                    AppTabs(state: state, destination: { id in
                        NoticeDestinationView(id: id, state: state)
                    })
                } else if state.loadFailed {
                    ContentUnavailableView {
                        Label("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text("앱을 다시 실행해 주세요.")
                    } actions: {
                        Button("다시 시도", action: state.loadCatalog)
                    }
                } else {
                    ProgressView("공고 불러오는 중")
                }
            }
        }
        .task { state.loadCatalog() }
    }
}
