import SwiftUI

@main struct DearbyApp: App {
    @State private var state: DiscoveryState?
    @State private var failure: String?
    var body: some Scene {
        WindowGroup {
            Group {
                if let state { DiscoveryRootView(state: state) } else {
                    ContentUnavailableView("저장소를 열 수 없습니다", systemImage: "externaldrive.badge.exclamationmark",
                        description: Text(failure ?? "저장소를 준비하고 있습니다."))
                }
            }.tint(Color(red: 0, green: 0.45, blue: 0.45)).preferredColorScheme(.light).task {
                guard state == nil else { return }
                do { state = try DiscoveryState.open() } catch { failure = error.localizedDescription }
            }
        }
    }
}
