import SwiftUI

struct SettingsPresentation<Content: View>: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @Bindable var viewModel: SettingsViewModel
    let startupFinished: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .task(id: startupFinished) {
                guard startupFinished else { return }
                await Task.yield()
                viewModel.preferences.start()
            }
            .sheet(isPresented: $viewModel.isPresented) {
                NavigationStack {
                    SettingsView(connection: viewModel.preferences.connection, isEnabled: viewModel.preferences.switchIsOn,
                        onToggle: viewModel.preferences.setEnabled,
                        onSettings: { openURL(URL(string: UIApplication.openSettingsURLString)!) })
                        .toolbar { Button("완료", action: viewModel.dismiss) }
                }
            }
            .alert("겹치는 일정 확인하기", isPresented: Binding(get: { viewModel.preferences.showFirstPrompt }, set: { _ in })) {
                Button("나중에", role: .cancel, action: viewModel.preferences.later)
                Button("켜기", action: viewModel.preferences.enableFromFirstPrompt)
            } message: {
                Text("캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.")
            }
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active: viewModel.preferences.lifecycle(.active)
                case .background: viewModel.preferences.lifecycle(.background)
                default: viewModel.preferences.lifecycle(.inactive)
                }
            }
    }
}
