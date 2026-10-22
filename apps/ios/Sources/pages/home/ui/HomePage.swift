import SwiftUI

/// Four tabs on the system tab bar (contract "명함 탭 통합" 2026-10-20; 2026-10-18 user decision: no custom bar). Detail pushes and the first-profile funnel hide it.
/// Tab symbols follow the contract's fixed name table (contract "아이콘 통일"); selection fills them (system default).
/// Every tab's navigation bar links to 설정 (`SettingsRoute`), so each stack gets the same destination here (`settings`).
struct HomePage<Discovery: View, Mine: View, Cards: View, Profile: View, Settings: View>: View {
    @Binding var selectedTab: Int
    /// The first-profile funnel takes the whole screen (no tab bar), like Toss.
    var hidesTabs = false
    // Discovery opens details from card buttons, so it gets the navigation path.
    let discovery: (Binding<NavigationPath>) -> Discovery
    // 내 활동 pushes its own routes too (`내 활동에서 보기` from a detail).
    let mine: (Binding<NavigationPath>) -> Mine
    let cards: Cards
    let profile: Profile
    /// The 설정 screen pushed from any tab's navigation bar.
    @ViewBuilder let settings: () -> Settings
    // Discovery pushes activity ids (String) and the settings route.
    @State private var discoveryPath = NavigationPath()
    // 내 활동 pushes three route types (progress track, activity detail, settings).
    @State private var minePath = NavigationPath()
    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("발견", systemImage: "safari", value: 0) {
                NavigationStack(path: $discoveryPath) { withSettings(discovery($discoveryPath)) }
                    .toolbar(discoveryPath.isEmpty ? .visible : .hidden, for: .tabBar)
            }
            Tab("내 활동", systemImage: "checklist", value: 1) {
                NavigationStack(path: $minePath) { withSettings(mine($minePath)) }
                    .toolbar(minePath.isEmpty ? .visible : .hidden, for: .tabBar)
            }
            Tab("명함", systemImage: "person.text.rectangle", value: 2) {
                NavigationStack { withSettings(cards) }
            }
            Tab("내 프로필", systemImage: "person.crop.circle", value: 3) {
                NavigationStack { withSettings(profile) }
                    .toolbar(hidesTabs ? .hidden : .visible, for: .tabBar)
            }
        }
        .tint(DearbyStyle.teal)
    }
    private func withSettings(_ root: some View) -> some View {
        root.navigationDestination(for: SettingsRoute.self) { _ in settings() }
    }
}
