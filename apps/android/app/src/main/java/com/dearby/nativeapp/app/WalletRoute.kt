package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.wallet.WalletPage
import com.dearby.nativeapp.widgets.card.cardContent.CardState

/** 받은 명함 tab: loads on entry, reloads after signing in from here. */
@Composable fun WalletRoute(model: WalletViewModel, account: AccountViewModel, open: (CardState) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    WalletPage(state, query, { query = it }, open, signIn = { signingIn = true }, retry = { model.load() })
    if (signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = { signingIn = false })
}
