import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog: CatalogViewModel
    @State private var identity = IdentityViewModel()
    @State private var share: QRShareModel
    @State private var tab = 0
    @Environment(\.scenePhase) private var scene
    // Captured from /s/<UUID> universal links; the shared-card screen is connected in a later step.
    @State private var incomingShareID: String?
    private let links: AppLinkProvider
    init() {
        let links = AppLinkProvider()
        self.links = links
        _catalog = State(initialValue: CatalogViewModel(fetch: links.catalog))
        _share = State(initialValue: QRShareModel(account: links.account(), link: links.shareURL))
    }
    var body: some Scene {
        WindowGroup {
            HomePage(selectedTab: $tab,
                discovery: { CatalogPage(state: catalog, path: $0) }, mine: MyActivitiesPage(state: catalog, explore: { tab = 0 }),
                qr: QRPage(state: identity, share: share,
                    activities: catalog.appliedActivities), wallet: WalletPage(state: identity), profile: ProfilePage(state: identity))
            .onOpenURL { incomingShareID = links.shareID($0) }
            .alert("공유 명함 링크", isPresented: Binding(get: { incomingShareID != nil }, set: { if !$0 { incomingShareID = nil } })) {
                Button("확인") {}
            } message: { Text("공유 명함 화면은 아직 연결되지 않았어요.") }
            .onChange(of: scene) { _, phase in
                if phase == .background { catalog.appLeft() } else if phase == .active { catalog.appReturned() }
            }
            .sheet(item: Binding(get: { catalog.asking }, set: { if $0 == nil { catalog.answer(.notYet) } })) { activity in
                ApplyConfirmationSheet(activityTitle: activity.title, applied: { catalog.answer(.applied) },
                                       notYet: { catalog.answer(.notYet) }, neverAsk: { catalog.answer(.neverAsk) })
                    .presentationDetents([.medium])
            }
        }
    }
}
