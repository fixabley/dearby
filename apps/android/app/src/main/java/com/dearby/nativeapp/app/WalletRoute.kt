package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.account.SignInSheet
import com.dearby.nativeapp.pages.wallet.WalletPage
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import kotlinx.coroutines.launch

/** 받은 명함 tab: loads on entry, reloads after signing in from here. */
@Composable fun WalletRoute(model: WalletViewModel, account: AccountViewModel, open: (CardState) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    WalletPage(state, query, collapsed, { query = it }, { id -> collapsed = if (id in collapsed) collapsed - id else collapsed + id },
        open, signIn = { signingIn = true }, retry = { model.load() })
    if (signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInSheet(auth.phase == AccountPhase.WORKING, auth.message,
        signIn = { scope.launch { account.signIn(Passkeys(context)) } }, signUp = { scope.launch { account.signUp(Passkeys(context)) } },
        close = { signingIn = false },
    )
}
