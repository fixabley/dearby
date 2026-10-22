import SwiftUI

@main struct DearbyApp: App {
    @State private var activityModel: ActivityViewModel
    @State private var cards: MyCardsModel
    @State private var profile: ProfileModel
    @State private var wallet: WalletModel
    @State private var tab = 0
    /// Which section the 명함 tab shows (contract "명함 탭 통합"); memory only.
    @State private var cardsSegment = CardsSegment.myCards
    /// First launch only; an account already on the device skips it.
    @State private var showIntro: Bool
    @Environment(\.scenePhase) private var scene
    private let links: ConnectionProvider
    private let account: AccountViewModel
    /// New key (2026-10-14) holding only "intro seen"; no earlier app data is read.
    private static let introShownKey = "dearby.onboarding.shown"
    init() {
        let links = ConnectionProvider()
        self.links = links
        let activityModel = ActivityViewModel(programs: links.programs, activities: links.programActivities)
        _activityModel = State(initialValue: activityModel)
        let account = links.account()
        self.account = account
        if account.phase == .signedIn { UserDefaults.standard.set(true, forKey: Self.introShownKey) }
        #if DEBUG
        if ScreenshotScene.current != nil { UserDefaults.standard.set(true, forKey: Self.introShownKey) }
        #endif
        _showIntro = State(initialValue: !UserDefaults.standard.bool(forKey: Self.introShownKey))
        // The QR sheet preselects the applied activity whose next stage is nearest; ended ones come last (contract "명함 교환").
        _cards = State(initialValue: MyCardsModel(account: account, webOrigin: links.origins.web) {
            activityModel.appliedProgress(now: Date()).map(\.activity)
        })
        _wallet = State(initialValue: WalletModel(account: account))
        _profile = State(initialValue: ProfileModel(account: account, webOrigin: links.origins.web))
    }
    var body: some Scene {
        WindowGroup {
            Group {
                if showIntro {
                    IntroPage(account: account, done: finishIntro)
                } else {
                    HomePage(selectedTab: $tab, hidesTabs: tab == 3 && profile.profile?.version == 0,
                        discovery: { ActivityListPage(state: activityModel, path: $0) }, mine: { MyActivitiesPage(state: activityModel, path: $0, explore: { tab = 0 }) },
                        cards: CardsPage(segment: $cardsSegment, cards: cards, wallet: wallet), profile: ProfilePage(state: profile),
                        settings: { SettingsPage(account: account, webOrigin: links.origins.web) })
                }
            }
            // One accent for every screen and sheet, including those presented from this root (contract "색 체계").
            .tint(DearbyStyle.teal)
            // "Dearby 앱에 저장" on the web card page: save the card to the account and open it in the 명함 tab's 받은 명함 section.
            .onOpenURL { url in
                // The Google SDK's own redirect comes back here first (contract "구글 로그인").
                if GoogleNativeSignIn.handle(url) { return }
                guard let incoming = WalletModel.Incoming(url: url) else { return }
                tab = 2
                cardsSegment = .received
                Task { await wallet.receive(incoming) }
            }
            #if DEBUG
            .task { await showScreenshotScene() }
            #endif
            // `내 활동에서 보기` on an activity detail: the 내 활동 tab opens that activity (it pushes once it is on screen).
            .onChange(of: activityModel.showInMine) { _, id in if id != nil { tab = 1 } }
            .onChange(of: scene) { _, phase in
                if phase == .background { activityModel.appLeft() } else if phase == .active { activityModel.appReturned() }
            }
            .sheet(item: Binding(get: { activityModel.asking }, set: { if $0 == nil { activityModel.answer(.notYet) } })) { activity in
                ApplyConfirmationSheet(activityTitle: activity.title, applied: { activityModel.answer(.applied) },
                                       notYet: { activityModel.answer(.notYet) }, neverAsk: { activityModel.answer(.neverAsk) })
                    .presentationDetents([.medium])
            }
        }
    }
    #if DEBUG
    /// Store screenshots: tab-level scenes; the discovery stack pushes its own (`ActivityListPage`). 내 활동 gets the first activity added.
    private func showScreenshotScene() async {
        switch ScreenshotScene.current {
        case .cards: tab = 2
        case .mine:
            if let id = await activityModel.screenshotActivity() { activityModel.apply(id, true) }
            tab = 1
        default: break
        }
    }
    #endif
    private func finishIntro() {
        UserDefaults.standard.set(true, forKey: Self.introShownKey)
        showIntro = false
    }
}
