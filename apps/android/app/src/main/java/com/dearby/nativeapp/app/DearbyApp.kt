package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.qr.ReceivedPhase
import com.dearby.nativeapp.pages.qr.ReceivedSharePage
import com.dearby.nativeapp.pages.qr.ReceivedShareState
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.applyPrompt.ApplyConfirmationSheet
import com.dearby.nativeapp.widgets.card.cardContent.CardState

private enum class Tab(val label: String, val icon: ImageVector) {
    Discovery("발견", Icons.Outlined.Explore), Mine("내 활동", Icons.Outlined.EventAvailable),
    Qr("QR", Icons.Outlined.QrCodeScanner), Wallet("받은 명함", Icons.Outlined.Badge), Profile("내 프로필", Icons.Outlined.PersonOutline),
}
private sealed interface CardRoute {
    data class Detail(val card: CardState) : CardRoute
    data object Composer : CardRoute
    data class Received(val link: ScannedLink) : CardRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DearbyApp(catalog: CatalogViewModel, account: AccountViewModel, publish: CardPublishViewModel, qrShare: QrShareViewModel, wallet: WalletViewModel, profile: ProfileViewModel, incoming: ScannedLink? = null, opened: () -> Unit = {}) {
    var tab by remember { mutableStateOf(Tab.Discovery) }
    var route by remember { mutableStateOf<CardRoute?>(null) }
    var catalogDetail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(route != null && route != CardRoute.Composer && route !is CardRoute.Received) { route = null }
    val compose = { publish.start(); route = CardRoute.Composer }
    // A /s/<UUID> App Link opens the shared card over whatever is showing.
    LaunchedEffect(incoming) { incoming?.let { route = CardRoute.Received(it); opened() } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (val screen = route) {
                    is CardRoute.Detail -> ReceivedSharePage(ReceivedShareState(ReceivedPhase.LOADED, screen.card), { route = null }, {}, { openContact(context, it) })
                    CardRoute.Composer -> CardComposerRoute(publish, account) { route = null }
                    is CardRoute.Received -> ReceivedShareRoute(screen.link, account) { route = null }
                    null -> when (tab) {
                        Tab.Discovery, Tab.Mine -> Column {
                            if (!catalogDetail) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                DearbyLogo(); Spacer(Modifier.weight(1f)); IconButton({ notice = "새 알림이 없어요.\n이 앱은 고정 예시로 둘러보는 프로토타입입니다." }) { Icon(Icons.Outlined.NotificationsNone, "알림") }
                            }
                            key(tab) { CatalogRoute(catalog, mine = tab == Tab.Mine, explore = { tab = Tab.Discovery }) { catalogDetail = it } }
                        }
                        Tab.Qr -> QrRoute(qrShare, catalog, compose) { route = CardRoute.Received(it) }
                        Tab.Wallet -> WalletRoute(wallet, account) { route = CardRoute.Detail(it) }
                        Tab.Profile -> ProfileRoute(profile, account, compose)
                    }
                }
            }
            if (route == null && !catalogDetail) {
                HorizontalDivider()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Tab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Teal, selectedTextColor = Teal, indicatorColor = Mint, unselectedIconColor = Quiet, unselectedTextColor = Quiet)) }
                }
            }
        }
    }
    // Contract #148 "신청 확인": ask once when the app comes back from the official application page.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> catalog.appLeft()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> catalog.appReturned()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val catalogState by catalog.state.collectAsStateWithLifecycle()
    catalogState.asking?.let { asking ->
        ModalBottomSheet({ catalog.answer(CatalogViewModel.ApplyAnswer.NOT_YET) }, containerColor = MaterialTheme.colorScheme.surface) {
            ApplyConfirmationSheet(asking.title, onApplied = { catalog.answer(CatalogViewModel.ApplyAnswer.APPLIED) },
                onNotYet = { catalog.answer(CatalogViewModel.ApplyAnswer.NOT_YET) }, onNeverAsk = { catalog.answer(CatalogViewModel.ApplyAnswer.NEVER_ASK) })
        }
    }
    notice?.let { value -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("안내") }, text = { Text(value) }, confirmButton = { TextButton({ notice = null }) { Text("확인") } }) }
}
