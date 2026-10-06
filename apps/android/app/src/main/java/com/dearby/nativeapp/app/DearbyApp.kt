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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.profile.ProfilePage
import com.dearby.nativeapp.pages.wallet.SendPage
import com.dearby.nativeapp.pages.wallet.SharedCardPage
import com.dearby.nativeapp.pages.wallet.WalletPage
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.applyPrompt.ApplyConfirmationSheet
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

private enum class Tab(val label: String, val icon: ImageVector) {
    Discovery("발견", Icons.Outlined.Explore), Mine("내 활동", Icons.Outlined.EventAvailable),
    Qr("QR", Icons.Outlined.QrCodeScanner), Wallet("받은 명함", Icons.Outlined.Badge), Profile("내 프로필", Icons.Outlined.PersonOutline),
}
private sealed interface CardRoute {
    data class Detail(val card: CardState) : CardRoute
    data class Send(val recipient: CardState) : CardRoute
    data object Composer : CardRoute
    data class Received(val link: ScannedLink) : CardRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DearbyApp(catalog: CatalogViewModel, demo: DemoViewModel, account: AccountViewModel, publish: CardPublishViewModel, qrShare: QrShareViewModel, incoming: ScannedLink? = null, opened: () -> Unit = {}) {
    val state by demo.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Discovery) }
    var route by remember { mutableStateOf<CardRoute?>(null) }
    var catalogDetail by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CardState?>(null) }
    val contact: (ContactState) -> Unit = { notice = "${it.label}\n${it.value}\n예시 연락처입니다." }
    BackHandler(route != null && route != CardRoute.Composer && route !is CardRoute.Received && preview == null) { route = null }
    val compose = { publish.start(); route = CardRoute.Composer }
    // A /s/<UUID> App Link opens the shared card over whatever is showing.
    LaunchedEffect(incoming) { incoming?.let { route = CardRoute.Received(it); opened() } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (val screen = route) {
                    is CardRoute.Detail -> SharedCardPage(screen.card, state.wallet.any { it.card.id == screen.card.id }, { route = null }, { demo.saveCard(screen.card) }, { route = CardRoute.Send(screen.card) }, contact)
                    is CardRoute.Send -> SendPage(state.cards, screen.recipient.person, { id ->
                        demo.send(id, screen.recipient); route = null; tab = Tab.Wallet; notice = "명함을 건네는 예시를 확인했어요.\n실제 전송은 하지 않았어요."
                    }, { route = null }, { preview = it }, contact)
                    CardRoute.Composer -> CardComposerRoute(publish, account) { route = null }
                    is CardRoute.Received -> ReceivedShareRoute(screen.link, account.client) { route = null }
                    null -> when (tab) {
                        Tab.Discovery, Tab.Mine -> Column {
                            if (!catalogDetail) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                DearbyLogo(); Spacer(Modifier.weight(1f)); IconButton({ notice = "새 알림이 없어요.\n이 앱은 고정 예시로 둘러보는 프로토타입입니다." }) { Icon(Icons.Outlined.NotificationsNone, "알림") }
                            }
                            key(tab) { CatalogRoute(catalog, mine = tab == Tab.Mine, explore = { tab = Tab.Discovery }) { catalogDetail = it } }
                        }
                        Tab.Qr -> QrRoute(qrShare, catalog, compose) { route = CardRoute.Received(it) }
                        Tab.Wallet -> WalletPage(state.wallet, state.query, state.reciprocalGroup, demo::query, demo::group, { route = CardRoute.Detail(it) }, { route = CardRoute.Send(it) }, contact)
                        Tab.Profile -> ProfilePage(state.profile, state.loggedIn, demo::profile, { demo.login(true) }, { demo.login(false) })
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
    preview?.let { card -> Dialog({ preview = null }) { Surface { Column(Modifier.padding(12.dp)) { CardContent(card, Modifier.heightIn(max = 540.dp), contact, expanded = true); TextButton({ preview = null }) { Text("닫기") } } } } }
}
