import SwiftUI

@main struct DearbyApp: App {
    @State private var catalog: CatalogViewModel
    @State private var share: QRShareModel
    @State private var profile: ProfileModel
    @State private var wallet: WalletModel
    @State private var tab = 0
    /// First launch only; an account already on the device skips it.
    @State private var showIntro: Bool
    @Environment(\.scenePhase) private var scene
    // A universal link opened the app; the QR tab turns it into the shared-card screen.
    @State private var opened: URL?
    private let links: AppLinkProvider
    private let account: AccountViewModel
    /// New key (2026-10-14) holding only "intro seen"; no earlier app data is read.
    private static let introShownKey = "dearby.onboarding.shown"
    init() {
        let links = AppLinkProvider()
        self.links = links
        _catalog = State(initialValue: CatalogViewModel(fetch: links.catalog))
        let account = links.account()
        self.account = account
        if account.phase == .signedIn { UserDefaults.standard.set(true, forKey: Self.introShownKey) }
        _showIntro = State(initialValue: !UserDefaults.standard.bool(forKey: Self.introShownKey))
        _share = State(initialValue: QRShareModel(account: account, link: links.shareURL))
        _wallet = State(initialValue: WalletModel(account: account))
        _profile = State(initialValue: ProfileModel(account: account))
    }
    var body: some Scene {
        WindowGroup {
            Group {
                if showIntro {
                    IntroPage(account: account, done: finishIntro)
                } else {
                    HomePage(selectedTab: $tab,
                        discovery: { CatalogPage(state: catalog, path: $0) }, mine: MyActivitiesPage(state: catalog, explore: { tab = 0 }),
                        qr: QRPage(share: share,
                            activities: catalog.appliedActivities, parse: links.scanned, opened: $opened), wallet: WalletPage(state: wallet), profile: ProfilePage(state: profile))
                }
            }
            .onOpenURL { url in
                guard links.shareID(url) != nil else { return }
                // A shared card link opens the card even on first launch.
                finishIntro()
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
    private func finishIntro() {
        UserDefaults.standard.set(true, forKey: Self.introShownKey)
        showIntro = false
    }
}
