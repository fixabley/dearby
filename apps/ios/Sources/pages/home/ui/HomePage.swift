import SwiftUI

struct HomePage<Discovery: View, Saved: View, QR: View, Wallet: View, Profile: View>: View {
    @Binding var selectedTab: Int
    let configured: Bool
    let discovery: Discovery
    let saved: Saved
    let qr: QR
    let wallet: Wallet
    let profile: Profile
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack { discovery }.tabItem { Label("발견", systemImage: "safari") }.tag(0)
            NavigationStack { saved }.tabItem { Label("저장", systemImage: "bookmark") }.tag(1)
            NavigationStack { qr }.tabItem { Label("QR", systemImage: "qrcode") }.tag(2)
            NavigationStack { wallet }.tabItem { Label("받은 명함", systemImage: "rectangle.stack") }.tag(3)
            NavigationStack { profile }.tabItem { Label("내 프로필", systemImage: "person.crop.circle") }.tag(4)
        }
        .safeAreaInset(edge: .top) {
            if !configured {
                Text("서버 미설정 · 저장된 정보는 유지됩니다").font(.caption).frame(maxWidth: .infinity)
                    .padding(6).background(.teal.opacity(0.08))
            }
        }
    }
}
