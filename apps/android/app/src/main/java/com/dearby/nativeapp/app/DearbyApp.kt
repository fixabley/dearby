package com.dearby.nativeapp.app

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
import com.dearby.nativeapp.widgets.recommendation.recommendationRows.RecommendationSection

/** Four tabs (contract "명함 탭 통합", 2026-10-20): 내 명함 and 받은 명함 are the two segments of 명함. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Discovery("발견", Icons.Rounded.Explore), Mine("내 활동", Icons.Rounded.Checklist), Cards("명함", Icons.Rounded.Badge), Profile("내 프로필", Icons.Rounded.AccountCircle),
}
/** A full-screen route pushed over the tabs (the tab bar hides). */
private sealed interface Route {
    /** A received card, by receipt id, so 내 명함도 건네기 shows the updated state. */
    data class Detail(val id: String) : Route
    /** 공유받은 명함 보기 for a card App Link (contract "유니버설 링크·App Links·활동 공유·오픈그래프"); the card lives in [WalletState.shared]. */
    data object Shared : Route
    /** 내 명함 QR 보이기 for one of the account's cards. */
    data class Qr(val card: PublishedCard) : Route
    data object Composer : Route
    /** 설정 (contract "설정"), opened from every tab's header. */
    data object Settings : Route
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DearbyApp(activityModel: ActivityViewModel, account: AccountViewModel, publish: CardPublishViewModel, myCards: MyCardsViewModel, wallet: WalletViewModel, profile: ProfileViewModel, settings: SettingsViewModel, recommendation: RecommendationViewModel, screenshot: String? = null) {
    var tab by remember { mutableStateOf(Tab.Discovery) }
    // The 명함 tab's segment, kept in memory for the app's lifetime (contract "명함 탭 통합").
    var cardsSegment by remember { mutableStateOf(CardsSegment.MINE) }
    // 내 활동에서 보기 (contract "내 활동 직접 추가"): the activity the 내 활동 tab should open next, consumed by its route.
    var mineOpen by remember { mutableStateOf<String?>(null) }
    // An App Link `/activities/<id>` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): the activity the 발견 tab should open next.
    var discoveryOpen by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<Route?>(null) }
    var activityDetail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Store screenshots (Debug only, `ScreenshotScene.kt`): the detail opens through 내 활동에서 보기; 내 활동 gets the first activity added.
    LaunchedEffect(screenshot) {
        when (screenshot) {
            "discover-expanded" -> activityModel.screenshotActivity()
            "detail" -> activityModel.screenshotActivity()?.let { mineOpen = it; tab = Tab.Mine }
            "mine" -> { activityModel.screenshotActivity()?.let { activityModel.apply(it, true) }; tab = Tab.Mine }
            "cards" -> tab = Tab.Cards
            "settings" -> route = Route.Settings
        }
    }
    BackHandler(route is Route.Detail || route is Route.Qr) { route = null }
    BackHandler(route == Route.Shared) { wallet.closeShared() }
    val compose = { publish.start(); route = Route.Composer }
    // The fixed header buttons of every tab (contract "알림 아이콘은 다섯 탭 모두 우상단 고정"·"설정"); the notification screen is still a placeholder notice.
    val openSettings = { route = Route.Settings }
    val notify = { notice = "새 알림이 없어요.\n이 앱은 고정 예시로 둘러보는 프로토타입입니다." }
    val profileState by profile.state.collectAsStateWithLifecycle()
    val walletState by wallet.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    // 명함 교환 (contract): a dearby://cards/… link saved a card → open it; signed out → sign in, then the link is saved.
    // A saved exchanged card opens on the 명함 tab's 받은 명함 segment with its detail pushed.
    // A card App Link not in the wallet opens 공유받은 명함 보기 over whatever was showing; once it closes (or saved), the route goes too.
    // Declared before the `opened` effect so a save's 받은 명함 detail, set in the same frame, wins.
    LaunchedEffect(walletState.shared) { if (walletState.shared != null) route = Route.Shared else if (route == Route.Shared) route = null }
    LaunchedEffect(walletState.opened) { walletState.opened?.let { tab = Tab.Cards; cardsSegment = CardsSegment.RECEIVED; activityDetail = false; route = Route.Detail(it); wallet.consumeOpened() } }
    // A linked activity, once loaded, opens on 발견 with its detail pushed over whatever was showing; a gone or unreadable one only says so.
    val activityListState by activityModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(activityListState.linked, activityListState.linkNotice) {
        activityListState.linked?.let { wallet.closeShared(); route = null; tab = Tab.Discovery; discoveryOpen = it }
        activityListState.linkNotice?.let { notice = it }
        if (activityListState.linked != null || activityListState.linkNotice != null) activityModel.consumeLinked()
    }
    // 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천"): the funnel takes the whole screen on the first visit to 발견 (not in store screenshots).
    val reco by recommendation.state.collectAsStateWithLifecycle()
    val recoFunnel by recommendation.funnel.collectAsStateWithLifecycle()
    val eligibilityForm by recommendation.form.collectAsStateWithLifecycle()
    LaunchedEffect(tab, route, reco.needsFunnel) { if (screenshot == null && tab == Tab.Discovery && route == null && reco.needsFunnel) recommendation.openFunnel(first = true) }
    val recoHeader: (@Composable () -> Unit)? = if (!reco.available || !reco.completed) null else {
        { RecommendationSection(reco.items, reco.personalized, activityModel::openLinked) { recommendation.openFunnel(first = false) } }
    }
    LaunchedEffect(auth.phase, walletState.needSignIn) { if (auth.phase == AccountPhase.SIGNED_IN && walletState.needSignIn) wallet.retryPending() }
    // Every screen sits on the theme `background`, which this Surface spreads under the status bar, navigation bar and keyboard too (contract "안전 영역
    // 배경"); cards, sheets and tiles are `surface` on top. Every screen inside starts with a `DearbyTopBar`, which takes the status-bar inset itself, so
    // only the bottom insets are padded here. The IME inset is applied once here (contract "키보드 내리기"): pages add `imeNestedScroll()` on their
    // scroll containers but no `imePadding()` of their own, and the tab bar is hidden while the keyboard is up.
    val imeVisible = WindowInsets.isImeVisible
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Box(Modifier.weight(1f)) {
                val funnel = recoFunnel
                if (funnel != null) {
                    BackHandler(onBack = recommendation::back)
                    // 추천 보러 가기 lands on 발견, where the 추천 묶음 is.
                    RecommendationFunnelPage(funnel, recommendation::toggle, { if (funnel.step == FunnelStep.RESULT) { tab = Tab.Discovery; route = null }; recommendation.next() },
                        recommendation::skip, recommendation::back, recommendation::retryTags)
                } else when (val screen = route) {
                    is Route.Detail -> walletState.cards.firstOrNull { it.id == screen.id }?.let { WalletCardRoute(it, wallet, myCards) { route = null } }
                        ?: LaunchedEffect(screen) { route = null }
                    Route.Shared -> walletState.shared?.let { SharedCardRoute(it, wallet, wallet::closeShared) }
                    is Route.Qr -> CardQrRoute(screen.card, activityModel, myCards) { route = null }
                    Route.Composer -> CardComposerRoute(publish, account) { route = null }
                    Route.Settings -> SettingsRoute(settings, account, profile) { route = null }
                    null -> when (tab) {
                        // 설정·알림 sit in each tab's pinned `DearbyTopBar` (contract "iOS 상단 바…" Android sentence).
                        Tab.Discovery, Tab.Mine -> key(tab) {
                            ActivityRoute(activityModel, mine = tab == Tab.Mine, explore = { tab = Tab.Discovery }, settings = openSettings, notify = notify,
                                openInMine = { mineOpen = it; tab = Tab.Mine }, open = if (tab == Tab.Mine) mineOpen else discoveryOpen, consumeOpen = { if (tab == Tab.Mine) mineOpen = null else discoveryOpen = null }, openConsent = { activityDetail = false; tab = Tab.Profile }, header = recoHeader) { activityDetail = it }
                        }
                        Tab.Cards -> CardsRoute(cardsSegment, { cardsSegment = it }, myCards, wallet, account, compose, showQr = { route = Route.Qr(it) },
                            edit = { card -> publish.start(card); route = Route.Composer }, openReceived = { route = Route.Detail(it.id) }, settings = openSettings, notify = notify)
                        Tab.Profile -> ProfileRoute(profile, account, recommendation, compose, openSettings, notify)
                    }
                }
            }
            // The first-profile funnel (never saved) takes the whole screen, like Toss: no tab bar until the profile exists.
            val settingUp = tab == Tab.Profile && profileState.settingUp
            // Stock Material 3 navigation bar with the theme's defaults (2026-10-18 user decision: no custom styling). The Column already pads the system bar.
            if (route == null && !activityDetail && !settingUp && !imeVisible && recoFunnel == null && !(tab == Tab.Profile && eligibilityForm != null)) {
                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Tab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) }
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
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> activityModel.appReturned()
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
    notice?.let { value -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton({ notice = null }) { Text("확인") } }) }
    reco.notice?.let { value -> AlertDialog(onDismissRequest = recommendation::dismissNotice, title = { Text("추천") }, text = { Text(value) }, confirmButton = { TextButton(recommendation::dismissNotice) { Text("확인") } }) }
    walletState.notice?.let { value -> AlertDialog(onDismissRequest = wallet::dismissNotice, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton(wallet::dismissNotice) { Text("확인") } }) }
    if (walletState.needSignIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = wallet::dismissSignIn)
}
