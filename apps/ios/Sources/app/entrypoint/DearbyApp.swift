import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog: CatalogViewModel
    @State private var identity = IdentityViewModel()
    @State private var share: QRShareModel
    @State private var profile: ProfileModel
    @State private var tab = 0
    @Environment(\.scenePhase) private var scene
    // A universal link opened the app; the QR tab turns it into the shared-card screen.
    @State private var opened: URL?
    private let links: AppLinkProvider
    init() {
        let links = AppLinkProvider()
        self.links = links
        _catalog = State(initialValue: CatalogViewModel(fetch: links.catalog))
        let account = links.account()
        _share = State(initialValue: QRShareModel(account: account, link: links.shareURL))
        _profile = State(initialValue: ProfileModel(account: account))
    }
    var body: some Scene {
        WindowGroup {
            HomePage(selectedTab: $tab,
                discovery: { CatalogPage(state: catalog, path: $0) }, mine: MyActivitiesPage(state: catalog, explore: { tab = 0 }),
                qr: QRPage(state: identity, share: share,
                    activities: catalog.appliedActivities, parse: links.scanned, opened: $opened), wallet: WalletPage(state: identity), profile: ProfilePage(state: profile))
            .onOpenURL { url in
                guard links.shareID(url) != nil else { return }
                tab = 2
                opened = url
            }
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
