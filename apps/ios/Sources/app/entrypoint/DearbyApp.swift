import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog = CatalogViewModel()
    @State private var identity = IdentityViewModel()
    @State private var tab = 0
    // Captured from /s/<UUID> universal links; the shared-card screen is connected in a later step.
    @State private var incomingShareID: String?
    private let links = AppLinkProvider()
    var body: some Scene {
        WindowGroup {
            HomePage(selectedTab: $tab,
                discovery: CatalogPage(state: catalog), saved: CatalogPage(state: catalog, saved: true),
                qr: QRPage(state: identity), wallet: WalletPage(state: identity), profile: ProfilePage(state: identity))
            .onOpenURL { incomingShareID = links.shareID($0) }
            .alert("공유 명함 링크", isPresented: Binding(get: { incomingShareID != nil }, set: { if !$0 { incomingShareID = nil } })) {
                Button("확인") {}
            } message: { Text("공유 명함 화면은 아직 연결되지 않았어요.") }
        }
    }
}
