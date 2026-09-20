import SwiftUI

struct ContentView: View {
    let session: AppSession

    var body: some View {
        SettingsPresentation(viewModel: session.settings, startupFinished: session.catalog != nil || session.loadFailed) {
            Group {
                if let catalog = session.catalog {
                    AppTabs(catalog: catalog, settings: session.settings, destination: { id in
                        NoticeDestinationView(id: id, session: session)
                    })
                } else if session.loadFailed {
                    ContentUnavailableView {
                        Label("공고를 불러오지 못했어요", systemImage: "exclamationmark.triangle")
                    } description: {
                        Text("앱을 다시 실행해 주세요.")
                    } actions: {
                        Button("다시 시도", action: session.loadCatalog)
                    }
                } else {
                    ProgressView("공고 불러오는 중")
                }
            }
        }
        .task { session.loadCatalog() }
    }
}
