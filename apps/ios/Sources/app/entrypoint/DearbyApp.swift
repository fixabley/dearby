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
        let activityModel = ActivityViewModel(programs: links.programs, activities: links.programActivities, activity: links.activity(id:),
                                              webOrigin: links.origins.web)
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
        // 공유받은 명함 보기's `<프로그램> <활동>에서 받은 명함` line names the activity in the link.
        _wallet = State(initialValue: WalletModel(account: account) { id in
            guard let activity = try? await links.activity(id: String(id)) else { return nil }
            return [activity.program, activity.title].compactMap { $0 }.joined(separator: " ")
        })
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
            // Contract "유니버설 링크·App Links·활동 공유·오픈그래프": card links open the 명함 tab's 받은 명함 section — `dearby://` (from
            // "Dearby 앱에 저장") saves right away, the web origin's `/cards/*` opens the saved detail or 공유받은 명함 보기; `/activities/*`
            // opens that activity in 발견.
            .onOpenURL { url in
                // The Google SDK's own redirect comes back here first (contract "구글 로그인").
                if GoogleNativeSignIn.handle(url) { return }
                switch IncomingLink(url: url, web: links.origins.web) {
                case .saveCard(let incoming):
                    tab = 2
                    cardsSegment = .received
                    Task { await wallet.receive(incoming) }
                case .viewCard(let incoming):
                    tab = 2
                    cardsSegment = .received
                    Task { await wallet.view(incoming) }
                case .activity(let id): Task { await activityModel.openLinked(id) }
                case nil: break
                }
            }
            #if DEBUG
            .task { await showScreenshotScene() }
            #endif
            // `내 활동으로` on an activity detail: the 내 활동 tab opens that activity (it pushes once it is on screen).
            .onChange(of: activityModel.showInMine) { _, id in if id != nil { tab = 1 } }
            .onChange(of: activityModel.showInDiscovery) { _, id in if id != nil { tab = 0 } }
            // 내 활동 follows the account (contract "내 활동 서버 저장"): signing in imports this run's marks once and then uses the server.
            .onChange(of: account.phase, initial: true) { _, phase in
                switch phase {
                case .signedIn: Task { await activityModel.signedIn(links.mineServer(account)) }
                case .signedOut: activityModel.signedOut()
                case .signingIn: break
                }
            }
            .alert(activityModel.notice ?? "", isPresented: Binding(get: { activityModel.notice != nil },
                                                                     set: { if !$0 { activityModel.clearNotice() } })) {
                Button("확인", role: .cancel) {}
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
    #if DEBUG
    /// Store screenshots: tab-level scenes; the discovery stack pushes its own (`ActivityListPage`). 내 활동 gets the first activity added.
    private func showScreenshotScene() async {
        switch ScreenshotScene.current {
        // 발견 장면은 마감 숨기기를 꺼 최근 활동 프로그램까지 보인다(빈 화면 방지).
        case .discover: activityModel.openOnly = false
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
