import SwiftUI

/// Five tabs on the system tab bar (2026-10-18 user decision: no custom bar). Detail pushes and the first-profile funnel hide it.
struct HomePage<Discovery: View, Mine: View, Cards: View, Wallet: View, Profile: View>: View {
    @Binding var selectedTab: Int
    /// The first-profile funnel takes the whole screen (no tab bar), like Toss.
    var hidesTabs = false
    // Discovery opens details from card buttons, so it gets the navigation path.
    let discovery: (Binding<[String]>) -> Discovery
    let mine: Mine
    let cards: Cards
    let wallet: Wallet
    let profile: Profile
    @State private var discoveryPath: [String] = []
    @State private var minePath: [String] = []
    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("발견", systemImage: "safari", value: 0) {
                NavigationStack(path: $discoveryPath) { discovery($discoveryPath) }
                    .toolbar(discoveryPath.isEmpty ? .visible : .hidden, for: .tabBar)
            }
            Tab("내 활동", systemImage: "calendar.badge.checkmark", value: 1) {
                NavigationStack(path: $minePath) { mine }
                    .toolbar(minePath.isEmpty ? .visible : .hidden, for: .tabBar)
            }
            Tab("내 명함", systemImage: "person.crop.rectangle", value: 2) { NavigationStack { cards } }
            Tab("받은 명함", systemImage: "person.text.rectangle", value: 3) { NavigationStack { wallet } }
            Tab("내 프로필", systemImage: "person.crop.circle", value: 4) {
                NavigationStack { profile }.toolbar(hidesTabs ? .hidden : .visible, for: .tabBar)
            }
        }
        .tint(DearbyStyle.teal).foregroundStyle(DearbyStyle.ink).preferredColorScheme(.light)
    }
}
