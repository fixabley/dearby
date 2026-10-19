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

private enum class Tab(val label: String, val icon: ImageVector) {
    Discovery("발견", Icons.Rounded.Explore), Mine("내 활동", Icons.Rounded.Checklist),
    Cards("내 명함", Icons.Rounded.Badge), Wallet("받은 명함", Icons.Rounded.Inbox), Profile("내 프로필", Icons.Rounded.AccountCircle),
}
/** A full-screen route pushed over the tabs (the tab bar hides). */
private sealed interface Route {
    /** A received card, by receipt id, so 내 명함도 건네기 shows the updated state. */
    data class Detail(val id: String) : Route
    /** 내 명함 QR 보이기 for one of the account's cards. */
    data class Qr(val card: PublishedCard) : Route
    data object Composer : Route
    /** 설정 (contract "설정"), opened from every tab's header. */
    data object Settings : Route
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DearbyApp(activityModel: ActivityViewModel, account: AccountViewModel, publish: CardPublishViewModel, myCards: MyCardsViewModel, wallet: WalletViewModel, profile: ProfileViewModel, settings: SettingsViewModel) {
    var tab by remember { mutableStateOf(Tab.Discovery) }
    var route by remember { mutableStateOf<Route?>(null) }
    var activityDetail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    BackHandler(route is Route.Detail || route is Route.Qr) { route = null }
    val compose = { publish.start(); route = Route.Composer }
    // The fixed header buttons of every tab (contract "알림 아이콘은 다섯 탭 모두 우상단 고정"·"설정"); the notification screen is still a placeholder notice.
    val openSettings = { route = Route.Settings }
    val notify = { notice = "새 알림이 없어요.\n이 앱은 고정 예시로 둘러보는 프로토타입입니다." }
    val profileState by profile.state.collectAsStateWithLifecycle()
    val walletState by wallet.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    // 명함 교환 (contract): a dearby://cards/… link saved a card → open it; signed out → sign in, then the link is saved.
    LaunchedEffect(walletState.opened) { walletState.opened?.let { tab = Tab.Wallet; activityDetail = false; route = Route.Detail(it); wallet.consumeOpened() } }
    LaunchedEffect(auth.phase, walletState.needSignIn) { if (auth.phase == AccountPhase.SIGNED_IN && walletState.needSignIn) wallet.retryPending() }
    // Every tab root sits on the theme `background` (the surfaceContainerLow tone, iOS systemGroupedBackground parity); cards, sheets and tiles are
    // `surface` on top. The status-bar inset is applied once here, so page headers add only their own 8dp. The IME inset is also applied once
    // here (contract "키보드 내리기"): pages add `imeNestedScroll()` on their scroll containers but no `imePadding()` of their own, and the tab bar is
    // hidden while the keyboard is up so no tab-bar band sits between the content and the keyboard.
    val imeVisible = WindowInsets.isImeVisible
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.weight(1f)) {
                when (val screen = route) {
                    is Route.Detail -> walletState.cards.firstOrNull { it.id == screen.id }?.let { WalletCardRoute(it, wallet, myCards) { route = null } }
                        ?: LaunchedEffect(screen) { route = null }
                    is Route.Qr -> CardQrRoute(screen.card, activityModel) { route = null }
                    Route.Composer -> CardComposerRoute(publish, account) { route = null }
                    Route.Settings -> SettingsRoute(settings, account, profile) { route = null }
                    null -> when (tab) {
                        // 설정·알림 sit in the tab's ScreenHeader (contract "탭 제목 통일·머리 간소화": no extra head row above the title).
                        Tab.Discovery, Tab.Mine -> key(tab) {
                            ActivityRoute(activityModel, mine = tab == Tab.Mine, explore = { tab = Tab.Discovery }, settings = openSettings, notify = notify) { activityDetail = it }
                        }
                        Tab.Cards -> MyCardsRoute(myCards, compose, showQr = { route = Route.Qr(it) }, settings = openSettings, notify = notify) { card -> publish.start(card); route = Route.Composer }
                        Tab.Wallet -> WalletRoute(wallet, account, openSettings, notify) { route = Route.Detail(it.id) }
                        Tab.Profile -> ProfileRoute(profile, account, compose, openSettings, notify)
                    }
                }
            }
            // The first-profile funnel (never saved) takes the whole screen, like Toss: no tab bar until the profile exists.
            val settingUp = tab == Tab.Profile && profileState.settingUp
            // Stock Material 3 navigation bar with the theme's defaults (2026-10-18 user decision: no custom styling). The Column already pads the system bar.
            if (route == null && !activityDetail && !settingUp && !imeVisible) {
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
    val activityListState by activityModel.state.collectAsStateWithLifecycle()
    activityListState.asking?.let { asking ->
        ModalBottomSheet({ activityModel.answer(ActivityViewModel.ApplyAnswer.NOT_YET) }, containerColor = MaterialTheme.colorScheme.surface) {
            ApplyConfirmationSheet(asking.title, onApplied = { activityModel.answer(ActivityViewModel.ApplyAnswer.APPLIED) },
                onNotYet = { activityModel.answer(ActivityViewModel.ApplyAnswer.NOT_YET) }, onNeverAsk = { activityModel.answer(ActivityViewModel.ApplyAnswer.NEVER_ASK) })
        }
    }
    notice?.let { value -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton({ notice = null }) { Text("확인") } }) }
    walletState.notice?.let { value -> AlertDialog(onDismissRequest = wallet::dismissNotice, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton(wallet::dismissNotice) { Text("확인") } }) }
    if (walletState.needSignIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = wallet::dismissSignIn)
}
