package com.dearby.nativeapp.app

import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.pages.profile.ConsentPage
import com.dearby.nativeapp.pages.profile.ProfilePhase
import com.dearby.nativeapp.shared.config.ConsentVersions
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.applyPrompt.ApplyConfirmationSheet
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.recommendation.FunnelStep
import com.dearby.nativeapp.pages.recommendation.RecommendationFunnelPage
import com.dearby.nativeapp.widgets.recommendation.recommendationRows.QueueEntryRow
import com.dearby.nativeapp.features.push.Push
import com.dearby.nativeapp.pages.inbox.InboxPage
import com.dearby.nativeapp.features.analytics.Analytics
import com.dearby.nativeapp.pages.activity.FavoriteHearts
import com.dearby.nativeapp.pages.cards.MyCardsPhase

/** Four tabs (contract "명함 탭 통합", 2026-10-20): 내 명함 and 받은 명함 are the two segments of 명함. */
private enum class Tab(val label: String, val icon: ImageVector, val code: String) {
    Discovery("발견", Icons.Rounded.Explore, "discovery"), Mine("내 활동", Icons.Rounded.Checklist, "my_activity"), Cards("명함", Icons.Rounded.Badge, "cards"), Profile("내 프로필", Icons.Rounded.AccountCircle, "profile"),
}
/** A full-screen route pushed over the tabs (the tab bar hides). */
private sealed interface Route {
    /** A received card, by receipt id, so 내 명함도 건네기 shows the updated state. */
    data class Detail(val id: String) : Route
    /** 공유받은 명함 보기 for a card App Link (contract "유니버설 링크·App Links·활동 공유·오픈그래프"); the card lives in [WalletState.shared]. */
    data object Shared : Route
    /** 내 명함 QR 보이기 for one of the account's cards; [activityId] presets the activity chip (오늘 내 행사 `명함 QR`, 2026-10-26). */
    data class Qr(val card: PublishedCard, val activityId: String? = null) : Route
    data object Composer : Route
    /** 설정 (contract "설정"), opened from every tab's header. */
    data object Settings : Route
    /** 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)"), from the `오늘의 맞춤 대기열` row atop 발견. */
    data object Queue : Route
    /** 알림 목록 (contract "알림 목록(발견 우상단 종)"), from the tab bars' bell. */
    data object Inbox : Route
    /** 개인정보 동의 (contract "개인정보 동의" 2026-10-25): signed in without the current consent, at launch, on return and from account features. */
    data object Consent : Route
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DearbyApp(activityModel: ActivityViewModel, account: AccountViewModel, publish: CardPublishViewModel, myCards: MyCardsViewModel, wallet: WalletViewModel, profile: ProfileViewModel, settings: SettingsViewModel, recommendation: RecommendationViewModel, queue: QueueViewModel,
    favorites: FavoritesViewModel, notifications: NotificationViewModel, inbox: InboxViewModel, device: DevicePreferences, sessions: SessionFavoritesViewModel, favoritesLink: Boolean = false, consumeFavoritesLink: () -> Unit = {},
    /** 설정 `세션 이동 알림` (device-local, 2026-10-26); MainActivity plans the alarms from it. */
    sessionReminders: Boolean = true, setSessionReminders: (Boolean) -> Unit = {}, screenshot: String? = null) {
    var tab by remember { mutableStateOf(Tab.Discovery) }
    // The 내 활동 tab's `내 활동 | 즐겨찾기` top tab, in memory for the app's lifetime like the 명함 tab's.
    var mineSegment by remember { mutableStateOf(MineSegment.MINE) }
    // 설정 `개인정보` `사용 통계 보내기` (2026-10-25), kept on the device.
    var analyticsOn by remember { mutableStateOf(device.analyticsEnabled) }
    // The 명함 tab's segment, kept in memory for the app's lifetime (contract "명함 탭 통합").
    var cardsSegment by remember { mutableStateOf(CardsSegment.MINE) }
    // 내 활동에서 보기 (contract "내 활동 직접 추가"): the activity the 내 활동 tab should open next, consumed by its route.
    var mineOpen by remember { mutableStateOf<String?>(null) }
    // An App Link `/activities/<id>` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): the activity the 발견 tab should open next.
    var discoveryOpen by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Route?>(null) }
    var activityDetail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 오늘의 맞춤 대기열: [queueReturn] reopens it after the detail its 자세히 opened.
    var queueReturn by remember { mutableStateOf(false) }
    // Store screenshots (Debug only, `ScreenshotScene.kt`): the detail opens through 내 활동에서 보기; 내 활동 gets the first activity added.
    LaunchedEffect(screenshot) {
        when (screenshot) {
            "discover-expanded" -> activityModel.screenshotActivity()
            "detail" -> activityModel.screenshotActivity()?.let { mineOpen = it; tab = Tab.Mine }
            "mine" -> { activityModel.screenshotActivity()?.let { activityModel.apply(it, true) }; tab = Tab.Mine }
            "cards" -> tab = Tab.Cards
            "settings" -> route = Route.Settings
            // 오늘의 맞춤 대기열 directly (signed in; no funnel, no `menu_open`).
            "queue" -> { queue.open(); route = Route.Queue }
            "favorites" -> { tab = Tab.Mine; mineSegment = MineSegment.FAVORITES }
        }
    }
    BackHandler(route is Route.Detail || route is Route.Qr) { route = null }
    BackHandler(route == Route.Shared) { wallet.closeShared() }
    // The fixed header buttons of every tab (contract "알림 아이콘은 다섯 탭 모두 우상단 고정"·"설정"); the notification screen is still a placeholder notice.
    // analytics-v1 "메뉴별 접근률": `menu_open` for the main menus, `tab_select` for a tapped tab.
    val openSettings = { Tracking.menu("settings"); route = Route.Settings }
    val profileState by profile.state.collectAsStateWithLifecycle()
    val walletState by wallet.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    // Signed out, making a card and the funnel→queue entry open the sign-in sheet instead (contract "비로그인에서 만들기·추천을 막음", 2026-10-25); `이 명함 고치기` only shows signed in.
    var signingIn by remember { mutableStateOf(false) }
    // The bell opens 알림 목록 (signed out → sign-in); [inboxReturn] brings it back after the detail one of its rows opened.
    var inboxReturn by remember { mutableStateOf(false) }
    val inboxState by inbox.state.collectAsStateWithLifecycle()
    // Signed in without the current consent the account features (and their data sync, [AccountViewModel.ready]) wait behind the consent screen.
    val ready by account.ready.collectAsStateWithLifecycle()
    val needAccount = { if (auth.phase == AccountPhase.SIGNED_IN) route = Route.Consent else signingIn = true }
    val notify = { if (ready) { Tracking.menu("notifications_inbox"); inbox.open(); route = Route.Inbox } else needAccount() }
    // Signed in from the `오늘의 맞춤 대기열` row: once the settings are read, a never-finished funnel opens (its result opens the queue), else the queue.
    var queueAfterSignIn by remember { mutableStateOf(false) }
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    // The consent state comes with the profile, read at sign-in and on every return to the app.
    LaunchedEffect(signedIn) { if (signedIn) { signingIn = false; profile.load() } }
    LaunchedEffect(ready) { Tracking.signedIn(ready) }
    val consentNeeded = signedIn && profileState.phase == ProfilePhase.LOADED && profileState.consentRequired
    var resumed by remember { mutableIntStateOf(0) }
    // Right after sign-up (profile version 0) the consent comes at once, over whatever screen the sign-in started from (설정 too).
    LaunchedEffect(consentNeeded, resumed) { if (consentNeeded && !signingIn && (route == null || profileState.settingUp)) route = Route.Consent }
    // Agreed (or signed out): the screen goes; right after sign-up on to 프로필 만들기.
    LaunchedEffect(consentNeeded) { if (!consentNeeded && route == Route.Consent) { route = null; if (profileState.settingUp) tab = Tab.Profile } }
    val consentScope = rememberCoroutineScope()
    val compose = { if (ready) { Tracking.menu("card_create"); publish.start(taken = myCards.state.value.cards.map { it.title }.toSet()); route = Route.Composer } else needAccount() }
    // 즐겨찾기 하트 (contract "즐겨찾기(하트)·새 모집 푸시 알림"): signed out a heart opens sign-in. The first program heart on Android 13+ asks for the
    // notification permission once and starts push (never at launch, never again after a refusal; 알림 in 내 프로필 then points to the system settings).
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> if (granted) notifications.register() }
    // Asked once in all, by whichever first heart comes first (contract "찜한 세션 이동 알림": the first session heart follows the first program heart's rule).
    val askNotificationsOnce = { if (!device.notificationsAsked) { device.notificationsAsked = true; if (Push.needsPermission(context)) askNotifications.launch(Push.PERMISSION) } }
    val activityListState by activityModel.state.collectAsStateWithLifecycle()
    val favoriteIds by favorites.ids.collectAsStateWithLifecycle()
    val hearts = FavoriteHearts(favoriteIds.activities, favoriteIds.programs, favoriteIds.levels, toggle = { program, id ->
        if (!ready) needAccount()
        else {
            // The first program heart starts push (token only from now on) and, on Android 13+, asks for the permission this once.
            if (program && id !in favoriteIds.programs && !device.pushStarted) { askNotificationsOnce(); notifications.start() }
            val adding = id !in (if (program) favoriteIds.programs else favoriteIds.activities)
            val details = if (program) listOfNotNull(activityListState.programs.firstOrNull { it.id == id }?.fieldTags?.firstOrNull()?.nameEn?.let { "field" to it.lowercase() }).toTypedArray()
                else activityModel.track(id, idKey = null)
            Analytics.event(if (adding) "favorite_add" else "favorite_remove", "type" to if (program) "program" else "activity", "id" to id, *details)
            favorites.toggle(program, id)
        }
    }, setLevel = { program, id, level -> Analytics.event("notify_level_set", "type" to if (program) "program" else "activity", "level" to level.name.lowercase()); favorites.setLevel(program, id, level) })
    // `오늘 일정 n개` (dearby://favorites): 내 활동 → 즐겨찾기.
    LaunchedEffect(favoritesLink) { if (favoritesLink) { route = null; wallet.closeShared(); tab = Tab.Mine; mineSegment = MineSegment.FAVORITES; consumeFavoritesLink() } }
    // 세션 찜 (contract "트랙·세션 시간표"): signed out the login sheet, signed in without consent the consent screen — as the activity hearts.
    // A first session heart asks the notification permission once too, for the 세션 이동 알림 the 찜 sets.
    val sessionIds by sessions.ids.collectAsStateWithLifecycle()
    val sessionHeart: (String) -> Unit = { id -> if (!ready) needAccount() else { if (id !in sessionIds) askNotificationsOnce(); sessions.toggle(id) } }
    val favoriteNotice by favorites.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val showNotice: (String) -> Unit = remember(snackbar) { { text -> snackbarScope.launch { snackbar.showSnackbar(text) } } }
    LaunchedEffect(favoriteNotice) { favoriteNotice?.let { try { snackbar.showSnackbar(it) } finally { favorites.dismissNotice() } } }
    val sessionNotice by sessions.notice.collectAsStateWithLifecycle()
    // While a timetable sheet is open the sheet shows it (ActivityRoute), since this Snackbar sits under the sheet.
    val timetableOpen = sessions.open.collectAsStateWithLifecycle().value != null
    LaunchedEffect(sessionNotice, timetableOpen) { sessionNotice?.takeIf { !timetableOpen }?.let { try { snackbar.showSnackbar(it) } finally { sessions.dismissNotice() } } }
    // 명함 교환 (contract): a dearby://cards/… link saved a card → open it; signed out → sign in, then the link is saved.
    // A saved exchanged card opens on the 명함 tab's 받은 명함 segment with its detail pushed.
    // A card App Link not in the wallet opens 공유받은 명함 보기 over whatever was showing; once it closes (or saved), the route goes too.
    // Declared before the `opened` effect so a save's 받은 명함 detail, set in the same frame, wins.
    LaunchedEffect(walletState.shared) { if (walletState.shared != null) route = Route.Shared else if (route == Route.Shared) route = null }
    LaunchedEffect(walletState.opened) { walletState.opened?.let { tab = Tab.Cards; cardsSegment = CardsSegment.RECEIVED; activityDetail = false; route = Route.Detail(it); wallet.consumeOpened() } }
    // A linked activity, once loaded, opens on 발견 with its detail pushed over whatever was showing; a gone or unreadable one only says so.
    LaunchedEffect(activityListState.linked, activityListState.linkNotice) {
        activityListState.linked?.let { wallet.closeShared(); route = null; tab = Tab.Discovery; discoveryOpen = it }
        activityListState.linkNotice?.let { notice = it; queueReturn = false; inboxReturn = false; sessions.dismissPending() }
        if (activityListState.linked != null || activityListState.linkNotice != null) activityModel.consumeLinked()
    }
    // GitHub 연결 came back (contract "연락처 인증"): the profile editor in front again — after process death the tab was 발견 — unless the consent screen is due.
    LaunchedEffect(profileState.returned) { if (profileState.returned > 0) { if (route != Route.Consent) route = null; wallet.closeShared(); activityDetail = false; tab = Tab.Profile; profile.consumeReturned() } }
    // 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천"): the funnel takes the whole screen on the first signed-in visit to 발견 (not in store screenshots).
    val reco by recommendation.state.collectAsStateWithLifecycle()
    val recoFunnel by recommendation.funnel.collectAsStateWithLifecycle()
    val eligibilityForm by recommendation.form.collectAsStateWithLifecycle()
    LaunchedEffect(tab, route, reco.needsFunnel, ready) { if (screenshot == null && ready && tab == Tab.Discovery && route == null && reco.needsFunnel) recommendation.openFunnel(first = true, entry = "auto") }
    // 오늘의 맞춤 대기열: its 자세히 opens the activity on 발견 and, once that detail closes, the queue comes back where it was.
    LaunchedEffect(activityDetail) {
        if (!activityDetail && queueReturn) { queueReturn = false; route = Route.Queue }
        if (!activityDetail && inboxReturn) { inboxReturn = false; route = Route.Inbox }
    }
    // 2026-10-25 user decision "추천은 없애고 맞춤대기열 진입만 남겨줘": one `오늘의 맞춤 대기열` row tops 발견; a never-finished funnel comes first and its
    // result button (`맞춤 대기열 보러 가기`) opens the queue. The row always shows; a failed read is the queue screen's own retry state. Signed out it
    // asks for sign-in and then carries on by itself ([queueAfterSignIn]).
    val openQueue = { Tracking.menu("queue"); queue.open(); route = Route.Queue }
    val enterQueue = { if (reco.completed) openQueue() else recommendation.openFunnel(first = true, entry = "queue_row") }
    LaunchedEffect(ready, reco.preferencesRead, queueAfterSignIn) { if (queueAfterSignIn && ready && reco.preferencesRead) { queueAfterSignIn = false; enterQueue() } }
    val recoHeader: @Composable () -> Unit = {
        QueueEntryRow(ready, signedIn) { if (ready) enterQueue() else { needAccount(); queueAfterSignIn = true } }
    }
    // 오늘 내 행사 `명함 QR` (2026-10-26): the chosen card's QR with the activity chip on that activity. The cards are read on the 명함 tab, so when
    // they are not in yet the tab opens on 내 명함, whose read then brings the QR up ([qrActivity]); no card (or a failed read) just leaves the tab.
    val myCardsState by myCards.state.collectAsStateWithLifecycle()
    var qrActivity by remember { mutableStateOf<String?>(null) }
    val showTodayQr: (String) -> Unit = { id ->
        val card = myCardsState.selectedId?.let(myCards::card)
        // `load()` first: a stale SIGNED_OUT·NO_CARD·FAILED phase from an earlier visit would otherwise drop [qrActivity] before 내 명함's own read starts.
        if (card != null) route = Route.Qr(card, id) else { qrActivity = id; myCards.load(); tab = Tab.Cards; cardsSegment = CardsSegment.MINE }
    }
    LaunchedEffect(myCardsState.phase, myCardsState.selectedId, qrActivity) {
        val id = qrActivity ?: return@LaunchedEffect
        when (myCardsState.phase) {
            MyCardsPhase.LOADING -> Unit
            MyCardsPhase.LOADED -> myCardsState.selectedId?.let(myCards::card)?.let { route = Route.Qr(it, id); qrActivity = null }
            else -> qrActivity = null
        }
    }
    // A card link to save: after sign-in and consent it carries on; signed in without consent it asks for the consent first.
    LaunchedEffect(ready, walletState.needSignIn) { if (ready && walletState.needSignIn) wallet.retryPending() }
    LaunchedEffect(signedIn, ready, walletState.needSignIn) { if (signedIn && !ready && walletState.needSignIn && consentNeeded) route = Route.Consent }
    // Every screen sits on the theme `background`, which this Surface spreads under the status bar, navigation bar and keyboard too (contract "안전 영역
    // 배경"); cards, sheets and tiles are `surface` on top. Every screen inside starts with a `DearbyTopBar`, which takes the status-bar inset itself, so
    // only the bottom insets are padded here. The IME inset is applied once here (contract "키보드 내리기"): pages add `dismissKeyboardOnScroll()` on their
    // scroll containers but no `imePadding()` of their own, and the tab bar is hidden while the keyboard is up.
    // 사용 통계: one `screen_view` per screen shown (analytics-v1 screen names), nothing else about the screen.
    val favoritesView by favorites.state.collectAsStateWithLifecycle()
    val screenName = when {
        recoFunnel != null -> "funnel"
        route is Route.Detail -> "received_card"
        route == Route.Shared -> "shared_card"
        route is Route.Qr -> "card_qr"
        route == Route.Composer -> "card_create"
        route == Route.Settings -> "settings"
        route == Route.Queue -> "queue"
        route == Route.Inbox -> "notifications_inbox"
        route == Route.Consent -> "consent"
        activityDetail -> "activity_detail"
        tab == Tab.Discovery -> "discovery"
        tab == Tab.Mine -> if (mineSegment != MineSegment.FAVORITES) "my_activity" else "favorites"
        tab == Tab.Cards -> if (cardsSegment == CardsSegment.MINE) "my_cards" else "received_cards"
        else -> "profile"
    }
    // Logged on every change and on coming back to the app (a view logged while in the background is dropped by Firebase).
    androidx.lifecycle.compose.LifecycleResumeEffect(screenName) { Analytics.screen(screenName); onPauseOrDispose {} }
    val imeVisible = WindowInsets.isImeVisible
    // One Snackbar for screens below (연락처 복사, 2026-10-26 "연락처 아이콘·바로가기").
    CompositionLocalProvider(LocalNotice provides showNotice) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Box(Modifier.weight(1f)) {
                val funnel = recoFunnel
                if (funnel != null) {
                    BackHandler(onBack = recommendation::back)
                    // 맞춤 대기열 보러 가기 lands on 발견 and opens the queue.
                    RecommendationFunnelPage(funnel, recommendation::toggle, { if (funnel.step == FunnelStep.RESULT) { tab = Tab.Discovery; route = null; openQueue() }; recommendation.next() },
                        recommendation::skip, recommendation::back, recommendation::retryTags)
                } else when (val screen = route) {
                    is Route.Detail -> walletState.cards.firstOrNull { it.id == screen.id }?.let { WalletCardRoute(it, wallet, myCards) { route = null } }
                        ?: LaunchedEffect(screen) { route = null }
                    Route.Shared -> walletState.shared?.let { SharedCardRoute(it, wallet, wallet::closeShared) }
                    is Route.Qr -> CardQrRoute(screen.card, activityModel, myCards, screen.activityId) { route = null }
                    Route.Composer -> CardComposerRoute(publish, account) {
                        (publish.state.value.phase as? PublishPhase.Published)?.let { myCards.preselect(it.card.id) }
                        route = null
                    }
                    // Turning 세션 이동 알림 on asks the notification permission once too (contract "찜한 세션 이동 알림").
                    Route.Settings -> SettingsRoute(settings, account, profile, if (ready) notifications else null, analyticsOn, { analyticsOn = it; device.analyticsEnabled = it; Analytics.setEnabled(it) },
                        sessionReminders, { on -> if (on) askNotificationsOnce(); setSessionReminders(on) }) { route = null }
                    Route.Consent -> {
                        BackHandler { route = null }
                        ConsentPage(profileState.consenting, profileState.consentError,
                            openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) },
                            openTerms = { OAuthBrowser(context).open(ConsentVersions.termsUrl(BuildConfig.WEB_ORIGIN)) },
                            agree = profile::consent, close = { route = null },
                            // Right after sign-up (no profile saved) 가입 취소 deletes the account; an account asked again for a new version gets 로그아웃.
                            cancelSignUp = if (profileState.profile?.version == 0) {
                                { consentScope.launch { runCatching { account.deleteAccount() }.onFailure { notice = "가입을 취소하지 못했어요. 잠시 후 다시 시도해 주세요." }; profile.load() } }
                            } else null,
                            signOut = { consentScope.launch { account.signOut(); profile.load() } }, renewal = profileState.profile?.consent != null)
                    }
                    Route.Inbox -> {
                        BackHandler { route = null }
                        InboxPage(inboxState, { route = null }, { row -> inbox.read(row.id); inboxReturn = true; activityModel.openLinked(row.activityId, "inbox") }, inbox::readAll, inbox::open, inbox::loadMore)
                    }
                    Route.Queue -> QueueRoute(queue, openDetail = { queueReturn = true; activityModel.openLinked(it, "queue") }, liked = favoriteIds.activities,
                        inMine = activityListState.known.values.filter { it.applied }.map { it.id }.toSet(),
                        like = { id, force ->
                            if ((force ?: (id !in favoriteIds.activities)) != (id in favoriteIds.activities)) {
                                if (id !in favoriteIds.activities) queue.liked(id)
                                hearts.toggle(false, id)
                            }
                        },
                        plus = { id ->
                            if (activityListState.known[id]?.applied == true) { queue.close(); route = null; mineSegment = MineSegment.MINE; mineOpen = id; tab = Tab.Mine }
                            else { queue.model(id)?.let { activityModel.adopt(listOf(it)) }; activityModel.apply(id, true, "queue") }
                        }) { route = null }
                    null -> when (tab) {
                        // 설정·알림 sit in each tab's pinned `DearbyTopBar` (contract "iOS 상단 바…" Android sentence).
                        Tab.Discovery, Tab.Mine -> key(tab) {
                            ActivityRoute(activityModel, mine = tab == Tab.Mine, explore = { tab = Tab.Discovery }, settings = openSettings, notify = notify,
                                openInMine = { mineOpen = it; mineSegment = MineSegment.MINE; tab = Tab.Mine }, open = if (tab == Tab.Mine) mineOpen else discoveryOpen, consumeOpen = { if (tab == Tab.Mine) mineOpen = null else discoveryOpen = null }, openConsent = { activityDetail = false; route = Route.Consent }, header = recoHeader,
                                hearts = hearts, favorites = favorites, segment = mineSegment, selectSegment = { mineSegment = it; Tracking.menu(if (it == MineSegment.FAVORITES) "favorites" else "my_activity_list") }, signIn = needAccount, unread = inboxState.unread,
                                sessions = sessions, sessionHeart = sessionHeart, ready = ready, showQr = showTodayQr) { activityDetail = it }
                        }
                        Tab.Cards -> CardsRoute(consentNeeded, { route = Route.Consent }, cardsSegment, { cardsSegment = it; Tracking.menu(if (it == CardsSegment.MINE) "my_cards" else "received_cards") }, myCards, wallet, account, compose, showQr = { route = Route.Qr(it) },
                            edit = { card -> publish.start(card); route = Route.Composer }, openReceived = { route = Route.Detail(it.id) }, settings = openSettings, notify = notify, unread = inboxState.unread)
                        Tab.Profile -> ProfileRoute(profile, account, recommendation, compose, openSettings, notify, leaveSetup = { tab = Tab.Discovery }, askConsent = { route = Route.Consent })
                    }
                }
                SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
            // The first-profile funnel (never saved) takes the whole screen, like Toss: no tab bar until the profile exists.
            val settingUp = tab == Tab.Profile && profileState.settingUp
            // Stock Material 3 navigation bar with the theme's defaults (2026-10-18 user decision: no custom styling). The Column already pads the system bar.
            if (route == null && !activityDetail && !settingUp && !imeVisible && recoFunnel == null && !(tab == Tab.Profile && eligibilityForm != null)) {
                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Tab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { if (tab != item) Analytics.event("tab_select", "tab" to item.code); tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) }
                }
            }
        }
    }
    }
    // Contract #148 "신청 확인": ask once when the app comes back from the official application page.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> activityModel.appLeft()
                // The consent is read again on every return and asked again while missing (contract "개인정보 동의" 2026-10-25).
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { activityModel.appReturned(); inbox.refreshCount(); if (account.state.value.phase == AccountPhase.SIGNED_IN) { profile.load(); resumed++ } }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    activityListState.asking?.let { asking ->
        ModalBottomSheet({ activityModel.answer(ActivityViewModel.ApplyAnswer.NOT_YET) }, containerColor = MaterialTheme.colorScheme.surface) {
            ApplyConfirmationSheet(asking.title, onApplied = { activityModel.answer(ActivityViewModel.ApplyAnswer.APPLIED) },
                onNotYet = { activityModel.answer(ActivityViewModel.ApplyAnswer.NOT_YET) }, onNeverAsk = { activityModel.answer(ActivityViewModel.ApplyAnswer.NEVER_ASK) })
        }
    }
    // 지난 선발 단계 자동 통과 제안 (user 2026-10-26), right after any add to 내 활동 (`+`, 내 활동에 추가, 신청했어요), over whichever screen made it.
    activityListState.autopass?.let { prompt ->
        AlertDialog(onDismissRequest = { activityModel.autopass(false) }, title = { Text("지난 선발 단계를 통과로 표시할까요?") },
            text = { Text("${prompt.stages.size}개 단계(${prompt.names.joinToString(", ")})가 이미 지났어요. " +
                if (prompt.remaining.isEmpty()) "통과로 표시하면 바로 참여 중으로 보여요." else "통과로 표시해요. 남은 단계(${prompt.remaining.joinToString(", ")})는 그대로 둬요.") },
            confirmButton = { TextButton({ activityModel.autopass(true) }) { Text("통과로 표시") } },
            dismissButton = { TextButton({ activityModel.autopass(false) }) { Text("그대로 두기") } })
    }
    notice?.let { value -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton({ notice = null }) { Text("확인") } }) }
    reco.notice?.let { value -> AlertDialog(onDismissRequest = recommendation::dismissNotice, title = { Text("추천") }, text = { Text(value) }, confirmButton = { TextButton(recommendation::dismissNotice) { Text("확인") } }) }
    walletState.notice?.let { value -> AlertDialog(onDismissRequest = wallet::dismissNotice, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton(wallet::dismissNotice) { Text("확인") } }) }
    if (walletState.needSignIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = wallet::dismissSignIn)
    else if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false; queueAfterSignIn = false })
}
