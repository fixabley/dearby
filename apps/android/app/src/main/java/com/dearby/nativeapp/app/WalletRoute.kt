package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.cards.MyCardsPhase
import com.dearby.nativeapp.pages.wallet.ReceivedCard
import com.dearby.nativeapp.pages.wallet.WalletCardPage
import com.dearby.nativeapp.pages.wallet.WalletPage

/** 받은 명함 tab: loads on entry, reloads after signing in from here. */
@Composable fun WalletRoute(model: WalletViewModel, account: AccountViewModel, settings: () -> Unit, notify: () -> Unit, open: (ReceivedCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    WalletPage(state, query, { query = it }, open, signIn = { signingIn = true }, retry = { model.load() }, settings = settings, notify = notify)
    if (signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = { signingIn = false })
}

/** One received card (contract "받은 명함 상세"): 내 명함도 건네기 needs the account's own cards, loaded here if the tab never was. */
@Composable fun WalletCardRoute(received: ReceivedCard, wallet: WalletViewModel, myCards: MyCardsViewModel, close: () -> Unit) {
    val state by wallet.state.collectAsStateWithLifecycle()
    val mine by myCards.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (mine.phase != MyCardsPhase.LOADED) myCards.load() }
    WalletCardPage(received, mine.cards.map { it.id to it.title }, state.busy, state.message, close, { openContact(context, it) }) { myCardId ->
        wallet.reciprocate(received.card.id, myCardId)
    }
}
