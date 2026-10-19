import SwiftUI

@main struct DearbyApp: App {
    @State private var activityModel: ActivityViewModel
    @State private var cards: MyCardsModel
    @State private var profile: ProfileModel
    @State private var wallet: WalletModel
    @State private var tab = 0
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
        _showIntro = State(initialValue: !UserDefaults.standard.bool(forKey: Self.introShownKey))
        // The QR sheet preselects the applied activity whose next stage is nearest; ended ones come last (contract "명함 교환").
        _cards = State(initialValue: MyCardsModel(account: account, webOrigin: links.origins.web) {
            activityModel.appliedProgress(now: Date()).map(\.activity)
        })
        _wallet = State(initialValue: WalletModel(account: account))
        _profile = State(initialValue: ProfileModel(account: account))
    }
    var body: some Scene {
        WindowGroup {
            Group {
                if showIntro {
                    IntroPage(account: account, done: finishIntro)
                } else {
                    HomePage(selectedTab: $tab, hidesTabs: tab == 4 && profile.profile?.version == 0,
                        discovery: { ActivityListPage(state: activityModel, path: $0) }, mine: MyActivitiesPage(state: activityModel, explore: { tab = 0 }),
                        cards: MyCardsPage(state: cards), wallet: WalletPage(state: wallet), profile: ProfilePage(state: profile))
                }
            }
            // "Dearby 앱에 저장" on the web card page: save the card to the account and open it in 받은 명함.
            .onOpenURL { url in
                // The Google SDK's own redirect comes back here first (contract "구글 로그인").
                if GoogleNativeSignIn.handle(url) { return }
                guard let incoming = WalletModel.Incoming(url: url) else { return }
                tab = 3
                Task { await wallet.receive(incoming) }
            }
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
    private func finishIntro() {
        UserDefaults.standard.set(true, forKey: Self.introShownKey)
        showIntro = false
    }
}
