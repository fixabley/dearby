import SwiftUI

@main struct DearbyApp: App {
    /// APNs token and notification taps (contract "즐겨찾기(하트)·새 모집 푸시 알림" 2026-10-25).
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var activityModel: ActivityViewModel
    @State private var favorites = FavoritesModel()
    @State private var sessionFavorites = SessionFavoritesModel()
    /// 찜한 세션 이동 알림 (contract "찜한 세션 이동 알림" 2026-10-26): local, rebuilt from the favorites on the device.
    @State private var sessionReminders = SessionReminderModel()
    /// 행사 당일 진행 중 표시 (contract "행사 당일 진행 중 표시" 2026-10-26): the Live Activity, built on the device from 내 활동 and 내 시간표.
    @State private var liveSession = LiveSessionModel()
    @State private var inbox = InboxModel()
    @State private var notifications: NotificationModel
    /// Which half of the 내 활동 tab shows (memory only); the summary notification opens 즐겨찾기.
    @State private var mineSegment = MineSegment.mine
    @State private var cards: MyCardsModel
    @State private var profile: ProfileModel
    @State private var wallet: WalletModel
    @State private var recommendations: RecommendationModel
    @State private var queue: DiscoveryQueueModel
    @State private var adConsent: AdConsentModel
    @State private var showsQueue = false
    /// Signed out, the queue row, its 설정 and 프로필 `관심 분야·활동 종류` open sign-in (contract "비로그인에서 만들기·추천을 막음" 2026-10-25);
    /// once signed in and the account's answers are read, `afterSignIn` continues where the tap was going.
    @State private var signingIn = false
    @State private var afterSignIn: AfterSignIn?
    /// `funnel` carries the 사용 통계 `funnel_start` entry (`queue_row` or `profile`).
    private enum AfterSignIn { case queue, funnel(String) }
    @State private var eligibility: EligibilityModel
    /// 개인정보 동의 (contract "개인정보 동의" 2026-10-25): the consent sheet over the whole app, asked at launch, on return to the foreground and
    /// whenever an account feature is opened while the current versions are not agreed. Closing it keeps the session.
    @State private var showsConsent = false
    @State private var promptLayers = PromptLayers()
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
    /// Signed in without the current required consent: account features show the consent instead.
    private var consentNeeded: Bool { account.phase == .signedIn && profile.consentPending }
    /// Signed in, consent known and given: only then does the app sync or send account data (mine, favorites, inbox, push, recommendations, queue).
    private var accountReady: Bool { account.phase == .signedIn && profile.profile != nil && !profile.consentPending }
    init() {
        AppAnalytics.start()
        let links = ConnectionProvider()
        self.links = links
        let activityModel = ActivityViewModel(programs: links.programs(query:cursor:status:kind:categories:),
                                              activities: links.programActivities(programID:status:kind:categories:), activity: links.activity(id:),
                                              timetable: links.timetable(activityID:scheduleID:),
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
        _recommendations = State(initialValue: RecommendationModel(tags: links.tagOptions))
        _queue = State(initialValue: DiscoveryQueueModel())
        _adConsent = State(initialValue: AdConsentModel(webOrigin: links.origins.web))
        _eligibility = State(initialValue: EligibilityModel(account: account))
        let notifications = NotificationModel(account: account)
        _notifications = State(initialValue: notifications)
        // This device's push token leaves the account before 로그아웃 ends the session.
        account.willSignOut = { await notifications.signingOut($0) }
    }
    var body: some Scene {
        WindowGroup {
            liveSessionHooks(sessionHooks(accountSync(Group {
                if showIntro {
                    IntroPage(account: account, done: finishIntro)
                } else {
                    HomePage(selectedTab: $tab, hidesTabs: tab == 3 && profile.profile?.version == 0 && !profile.consentPending,
                        discovery: { ActivityListPage(state: activityModel, queueSignedIn: accountReady, queueNeedsConsent: consentNeeded, openQueue: queueRowTapped,
                                                      editRecommendations: { editRecommendations("queue_row") }, showQR: showCardQR, path: $0) }, mine: { MyActivitiesPage(state: activityModel, segment: $mineSegment, path: $0, explore: { tab = 0 }) },
                        cards: CardsPage(segment: $cardsSegment, cards: cards, wallet: wallet, consentNeeded: consentNeeded) { showsConsent = true },
                        profile: ProfilePage(state: profile, eligibility: eligibility,
                                                                                         editRecommendations: { editRecommendations("profile") },
                                                                                         leaveSetup: { tab = 0 },
                                             consentNeeded: consentNeeded, askConsent: { showsConsent = true }),
                        settings: { SettingsPage(account: account, webOrigin: links.origins.web, notifications: accountReady ? notifications : nil,
                                                 sessionReminders: accountReady ? sessionReminders : nil, liveSession: liveSession,
                                                 adConsent: accountReady ? adConsent : nil) },
                        inbox: { NotificationInboxPage(model: inbox, state: activityModel) })
                    .fullScreenCover(isPresented: $showsQueue) {
                        DiscoveryQueuePage(model: queue, state: activityModel, adConsent: adConsent)
                    }
                    .promptLayer()
                }
            }
            // Sign-in, consent and the favorites notices show on the top layer (`promptLayer`): the app root, or a sheet over it.
            .environment(\.promptHost, promptHost)
            // Every heart reads the one favorites model, also inside sheets and the queue.
            .environment(favorites)
            // Every tab's bell: the inbox signed in, sign-in otherwise (contract "알림 목록(발견 우상단 종)").
            .environment(\.inboxEntry, InboxEntry(signedIn: accountReady, unread: inbox.unread, signIn: needAccount))
            // Profile and card photos need the session (contract "프로필 사진"), so every `DearbyPhotoAvatar` reads through the account.
            .environment(\.photoLoader, PhotoLoader { [account] in try await account.photo($0) })
            // A heart pressed signed out opens the sign-in sheet (contract "즐겨찾기(하트)": 로그인만).
            .onChange(of: favorites.signInRequested) { _, requested in
                guard requested else { return }
                favorites.signInRequested = false
                needAccount()
            }
            .task {
                // The first program heart asks for notification permission (never at launch).
                favorites.programFavorited = { Task { await notifications.requestPermissionIfNeeded() } }
                // 사용 통계: a favorite's 분야·활동 종류 from discovery, the queue or the favorites list.
                favorites.describe = { [activityModel, favorites, queue] in Self.tags($0, activityModel, queue, favorites) }
            }
            // No push token reaches the server before consent; `accountReady` sends the one APNs gave meanwhile.
            .onChange(of: delegate.deviceToken) { _, token in if let token, accountReady { notifications.registered(token) } }
            .onChange(of: delegate.tappedNotification, initial: true) { _, id in
                guard let id else { return }
                delegate.tappedNotification = nil
                inbox.read(id)
            }
            .onChange(of: delegate.tapped, initial: true) { _, tap in
                guard let tap else { return }
                delegate.tapped = nil
                switch tap {
                case .link(let url): open(url, source: "push")
                case .activity(let id): Task { await activityModel.openLinked(id, source: "push") }
                case .timetable(let activityID, let scheduleID): Task { await activityModel.openLinkedTimetable(activityID, scheduleID: scheduleID) }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .NSSystemTimeZoneDidChange)) { _ in if accountReady { notifications.timeZoneChanged() } }
            // One accent for every screen and sheet, including those presented from this root (contract "색 체계").
            .tint(DearbyStyle.teal)
            // Contract "유니버설 링크·App Links·활동 공유·오픈그래프": card links open the 명함 tab's 받은 명함 section — `dearby://` (from
            // "Dearby 앱에 저장") saves right away, the web origin's `/cards/*` opens the saved detail or 공유받은 명함 보기; `/activities/*`
            // opens that activity in 발견.
            .onOpenURL { open($0) }
            #if DEBUG
            .task { await showScreenshotScene() }
            #endif
            // `내 활동으로` on an activity detail: the 내 활동 tab opens that activity (it pushes once it is on screen).
            .onChange(of: activityModel.showInMine) { _, id in if id != nil { tab = 1 } }
            .onChange(of: activityModel.showInDiscovery) { _, id in if id != nil { tab = 0 } }
            ))
            // The account's answers arrived after a sign-in from the queue row or 관심 분야·활동 종류 (the sheet may still be closing).
            .onChange(of: recommendations.ready) { _, _ in continueAfterSignIn() }
            // 추천 설정 퍼널 (contract "추천 설정 퍼널·지원 자격 정보·추천"): once, on the first visit to 발견 after the account's answers are read.
            .onChange(of: tab == 0 && !showIntro && !signingIn && !consentNeeded && recommendations.needsFunnel, initial: true) { _, show in
                #if DEBUG
                if ScreenshotScene.current != nil { return }
                #endif
                if show { presentFunnel("auto") }
            }
            .fullScreenCover(isPresented: $recommendations.presentingFunnel, onDismiss: {
                guard recommendations.queueAfterFunnel else { return }
                recommendations.queueAfterFunnel = false
                showsQueue = true
            }) {
                RecommendationFunnelPage(model: recommendations) { tab = 0 }
            }
            // Signed in, the server judges 지원 자격 with the saved values: a change reloads the recommendations.
            .onChange(of: eligibility.profile) { _, _ in
                recommendations.hasEligibility = eligibility.hasValues
                // 사용 통계: whether 지원 자격 정보 was entered, never its values.
                AppAnalytics.userProperty("has_eligibility", eligibility.hasValues ? "yes" : "no")
                Task { await recommendations.refresh() }
            }
            .alert(activityModel.notice ?? "", isPresented: Binding(get: { activityModel.notice != nil },
                                                                     set: { if !$0 { activityModel.clearNotice() } })) {
                Button("확인", role: .cancel) {}
            }
            .onChange(of: scene) { _, phase in
                if phase == .background { activityModel.appLeft() } else if phase == .active {
                    activityModel.appReturned()
                    // The permission may have changed in Settings; the consent is read again and asked again while missing.
                    if account.phase == .signedIn {
                        Task {
                            await profile.load()
                            if consentNeeded, !signingIn { showsConsent = true }
                        }
                    }
                    if accountReady {
                        Task { await notifications.refresh() }
                        Task { await inbox.refreshCount() }
                    }
                    // Past sessions drop out and a permission given in Settings takes effect; signed out this clears what is left.
                    sessionReminders.refresh()
                    // A new day, or the session boundary passed while the app was away.
                    syncLiveSession()
                }
            })
        }
    }
    /// 행사 당일 진행 중 표시: the Live Activity follows 내 활동, 내 시간표 and the switch (`syncLiveSession`), kept apart from `body` for the compiler.
    private func liveSessionHooks(_ content: some View) -> some View {
        content
            .onChange(of: activityModel.mineReady, initial: true) { _, _ in syncLiveSession() }
            .onChange(of: activityModel.appliedIDs) { _, _ in syncLiveSession() }
            .onChange(of: activityModel.stageMarks) { _, _ in syncLiveSession() }
            .onChange(of: sessionFavorites.items) { _, _ in syncLiveSession() }
            .onChange(of: sessionFavorites.phase) { _, _ in syncLiveSession() }
            .onChange(of: account.phase) { _, _ in syncLiveSession() }
            .onChange(of: liveSession.enabled) { _, _ in liveSession.refresh() }
    }
    /// Signed out, the activity ends. Signed in, the plan is rebuilt once 내 활동 and 내 시간표 were both read this run; before that (a cold
    /// launch, or offline at the venue) the saved plan keeps the display going.
    private func syncLiveSession() {
        #if DEBUG
        if liveSession.previewing { return }
        #endif
        if account.phase == .signedOut {
            liveSession.sync(nil)
        } else if accountReady, activityModel.mineReady, [.loaded, .unavailable].contains(sessionFavorites.phase) {
            let now = Date()
            liveSession.sync(.today(activityModel.appliedProgress(now: now), favorites: sessionFavorites.items, now: now), now: now)
        } else {
            liveSession.refresh()
        }
    }
    /// 세션 찜 (contract "트랙·세션 시간표"), kept apart from `body` so the compiler can type-check it: every session heart reads the one model;
    /// one pressed signed out or before the consent opens sign-in or the consent; a failed change says so.
    private func sessionHooks(_ content: some View) -> some View {
        content
            .environment(sessionFavorites)
            .onChange(of: sessionFavorites.signInRequested) { _, requested in
                guard requested else { return }
                sessionFavorites.signInRequested = false
                needAccount()
            }
            // 세션 이동 알림 follow the favorites: a heart, the list read after sign-in, sign-out (`[]` removes them all) and the switch.
            .onChange(of: sessionFavorites.items) { _, items in sessionReminders.reschedule(items) }
            // The first read of this run, also when it comes back empty or the server has no session favorites: stale reminders go.
            .onChange(of: sessionFavorites.phase) { _, phase in
                if phase == .loaded || phase == .unavailable { sessionReminders.reschedule(sessionFavorites.items) }
            }
            .onChange(of: sessionReminders.enabled) { _, _ in sessionReminders.refresh() }
            .task {
                // The first session heart asks for the notification permission (as the first program heart); granted, the reminders are set.
                sessionFavorites.sessionFavorited = { [notifications, sessionReminders] in
                    Task {
                        await notifications.requestPermissionIfNeeded()
                        sessionReminders.refresh()
                    }
                }
            }
    }
    /// The prompts every `promptLayer` carries, shown only on the top one (contract "트랙·세션 시간표" 2026-10-26 모달 시트): signed in from a
    /// timetable sheet, the sheet stays and its hearts follow the account.
    private var promptHost: PromptHost {
        PromptHost(layers: promptLayers) { content, isTop in
            AnyView(content
                .sheet(isPresented: Binding(get: { isTop && signingIn }, set: { if !$0 { signingIn = false } }), onDismiss: continueAfterSignIn) {
                    NavigationStack { SignInSheet(account: account) {} }
                }
                // Closes itself once agreed (the getter reads the pending state); pulled down or 닫기 it asks again next time.
                .sheet(isPresented: Binding(get: { isTop && showsConsent && consentNeeded }, set: { if !$0 { showsConsent = false } })) {
                    NavigationStack { ConsentPage(state: profile) }
                }
                .alert(favorites.notice ?? "", isPresented: Binding(get: { isTop && favorites.notice != nil }, set: { if !$0 { favorites.notice = nil } })) {
                    Button("확인", role: .cancel) {}
                }
                // `신청하셨나요?` and the 통과 제안 (contract "지난 선발 단계 통과 제안" 2026-10-26) on the top layer too: the queue cover and its
                // detail sheet carry `promptLayer`, so `+` there is asked where it was pressed.
                .activityPrompts(activityModel, isTop: isTop)
                .background {
                    // A separate host so it does not compete with the other alert.
                    Color.clear.alert(sessionFavorites.notice ?? "", isPresented: Binding(get: { isTop && sessionFavorites.notice != nil },
                                                                                           set: { if !$0 { sessionFavorites.notice = nil } })) {
                        Button("확인", role: .cancel) {}
                    }
                })
        }
    }
    /// Account sync and the consent prompts, kept apart from `body` so the compiler can type-check it.
    private func accountSync(_ content: some View) -> some View {
        content
        // 내 활동 follows the account (contract "내 활동 서버 저장"): signing in imports this run's marks once and then uses the server.
        .onChange(of: account.phase, initial: true) { _, phase in
            switch phase {
            case .signedIn:
                // The consent state comes with the profile; account sync waits for it (`accountReady`).
                Task { await profile.load() }
            case .signedOut:
                activityModel.signedOut()
                recommendations.signedOut()
                queue.signedOut()
                adConsent.signedOut()
                favorites.signedOut()
                sessionFavorites.signedOut()
                inbox.signedOut()
                // A failed attempt inside the open sheet keeps it; a sign-out later drops a continuation that never ran.
                if !signingIn { afterSignIn = nil }
            case .signingIn: break
            }
            if phase == .signedOut {
                AppAnalytics.userProperty("signed_in", "no")
                Task { await eligibility.load() }
            }
        }
        .onChange(of: accountReady, initial: true) { _, ready in
            guard ready else { return }
            AppAnalytics.userProperty("signed_in", "yes")
            Task { await eligibility.load() }
            Task { await activityModel.signedIn(links.mineServer(account)) }
            Task { await favorites.signedIn(links.favoritesServer(account)) }
            Task { await sessionFavorites.signedIn(links.sessionFavoritesServer(account)) }
            Task { await inbox.signedIn(links.inboxServer(account)) }
            Task { await notifications.refresh() }
            Task { await recommendations.signedIn(links.recommendationServer(account)) }
            Task { await queue.signedIn(links.discoveryQueueServer(account)) }
            adConsent.signedIn(links.adConsentServer(account))
            if let token = delegate.deviceToken { notifications.registered(token) }
        }
        // Asked at launch and after sign-in once the profile says so (not over the intro or the sign-in sheet; `continueAfterSignIn` asks then).
        .onChange(of: consentNeeded && !showIntro && !signingIn, initial: true) { _, ask in if ask { showsConsent = true } }
        // Agreed right after sign-up: on to 프로필 만들기.
        .onChange(of: profile.consentPending) { was, pending in
            if was, !pending, account.phase == .signedIn, profile.profile?.version == 0 { tab = 3 }
        }
    }
    /// 사용 통계 `field`/`category` of a favorite, from whichever list has it.
    private static func tags(_ target: FavoriteTarget, _ discovery: ActivityViewModel, _ queue: DiscoveryQueueModel,
                             _ favorites: FavoritesModel) -> [String: Any] {
        switch target {
        case .activity(let id):
            let activity = discovery.activity(id) ?? queue.items.first { $0.id == id }?.activity ?? favorites.activities.first { $0.id == id }?.activity
            return AppAnalytics.tags(activity?.fieldTags ?? [], activity?.category)
        case .program(let id):
            let program = discovery.programs.first { $0.id == id } ?? favorites.programs.first { $0.id == id }?.program
            return AppAnalytics.tags(program?.fieldTags ?? [], nil)
        }
    }
    /// A link the app was opened with, or a tapped notification's `url`; `source` is the 사용 통계 `activity_view` source of an activity it
    /// opens (`link`, or `push` for a tapped notification).
    private func open(_ url: URL, source: String = "link") {
        // The Google SDK's own redirect comes back here first (contract "구글 로그인").
        if GoogleNativeSignIn.handle(url) { return }
        switch IncomingLink(url: url, web: links.origins.web) {
        case .saveCard where consentNeeded, .viewCard where consentNeeded:
            tab = 2
            showsConsent = true
        case .saveCard(let incoming):
            tab = 2
            cardsSegment = .received
            Task { await wallet.receive(incoming) }
        case .viewCard(let incoming):
            tab = 2
            cardsSegment = .received
            Task { await wallet.view(incoming) }
        case .activity(let id): Task { await activityModel.openLinked(id, source: source) }
        // The Live Activity's tap (contract "행사 당일 진행 중 표시"): the detail with the block's timetable sheet on 내 시간표.
        case .timetable(let activityID, let scheduleID): Task { await activityModel.openLinkedTimetable(activityID, scheduleID: scheduleID, source: source) }
        case .favorites:
            tab = 1
            mineSegment = .favorites
        case nil: break
        }
    }
    #if DEBUG
    /// Store screenshots: tab-level scenes; the discovery stack pushes its own (`ActivityListPage`). 내 활동 gets the first activity added.
    private func showScreenshotScene() async {
        switch ScreenshotScene.current {
        // 발견 장면은 상태 묶음을 모두 골라 최근 활동 프로그램까지 보인다(빈 화면 방지).
        case .discover: activityModel.applyFilters(groups: Set(StatusGroup.allCases), categories: activityModel.categories)
        case .cards: tab = 2
        // 오늘의 맞춤 대기열 directly (signed in; no funnel, no `menu_open`).
        case .queue: showsQueue = true
        case .favorites:
            tab = 1
            mineSegment = .favorites
        case .mine:
            if let id = await activityModel.screenshotActivity() { activityModel.apply(id, true) }
            tab = 1
        default: break
        }
    }
    #endif
    /// `명함 QR` on the 오늘 내 행사 card (contract "오늘 내 행사 카드"): the 명함 tab's 내 명함 opens QR 보이기 for the selected card with this activity.
    private func showCardQR(_ activityID: String) {
        cards.qrActivityID = activityID
        cardsSegment = .myCards
        tab = 2
    }
    /// The 발견 row `오늘의 맞춤 대기열` (사용 통계 `menu_open(queue)` once per tap; a continuation after sign-in is not another tap).
    private func queueRowTapped() {
        AppAnalytics.event("menu_open", ["menu": "queue"])
        openQueue()
    }
    /// `오늘의 맞춤 대기열`: signed out, sign-in first; then the funnel when it was never finished, then the queue (after the funnel closes).
    private func openQueue() {
        if !accountReady {
            afterSignIn = .queue
            needAccount()
        } else if recommendations.needsFunnel {
            recommendations.queueAfterFunnel = true
            presentFunnel("queue_row")
        } else {
            showsQueue = true
        }
    }
    /// Reopens the 추천 funnel; signed out, sign-in first. `entry`: the queue row's 설정 (`queue_row`, menu `funnel`) or 프로필
    /// `관심 분야·활동 종류` (`profile`, menu `interests`).
    private func editRecommendations(_ entry: String) {
        AppAnalytics.event("menu_open", ["menu": entry == "profile" ? "interests" : "funnel"])
        if accountReady {
            presentFunnel(entry)
        } else {
            afterSignIn = .funnel(entry)
            needAccount()
        }
    }
    /// Shows the 추천 funnel; `entry` is its 사용 통계 `funnel_start` entry (`auto`, `queue_row`, `profile`).
    private func presentFunnel(_ entry: String) {
        recommendations.funnelEntry = entry
        recommendations.presentingFunnel = true
    }
    /// After the sign-in sheet closes: nothing when it closed without signing in; otherwise, once the answers are read, the queue or the funnel.
    /// Signed out → sign-in; signed in without consent → the consent sheet (the account features are behind it).
    private func needAccount() {
        if account.phase == .signedIn { showsConsent = true } else { signingIn = true }
    }
    private func continueAfterSignIn() {
        if consentNeeded, !signingIn { showsConsent = true }
        guard let next = afterSignIn, !signingIn else { return }
        guard account.phase == .signedIn else { afterSignIn = nil; return }
        guard recommendations.ready else { return }
        afterSignIn = nil
        switch next {
        case .queue: openQueue()
        case .funnel(let entry): presentFunnel(entry)
        }
    }
    private func finishIntro() {
        UserDefaults.standard.set(true, forKey: Self.introShownKey)
        showIntro = false
    }
}
