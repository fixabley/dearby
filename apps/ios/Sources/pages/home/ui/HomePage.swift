import SwiftUI

struct HomePage<QR: View, Wallet: View, Profile: View>: View {
    @Binding var selectedTab: Int
    let configured: Bool
    let qr: QR
    let wallet: Wallet
    let profile: Profile
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                ContentUnavailableView("모집 중 활동 준비 중", systemImage: "safari",
                    description: Text("공식 출처와 갱신 상태를 확인할 수 있는 활동 목록을 연결하고 있습니다."))
                    .navigationTitle("발견")
            }.tabItem { Label("발견", systemImage: "safari") }.tag(0)
            NavigationStack {
                ContentUnavailableView("저장한 활동", systemImage: "bookmark",
                    description: Text("활동 서비스 연결 후 저장한 조직과 프로그램을 확인할 수 있습니다."))
                    .navigationTitle("저장")
            }.tabItem { Label("저장", systemImage: "bookmark") }.tag(1)
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
