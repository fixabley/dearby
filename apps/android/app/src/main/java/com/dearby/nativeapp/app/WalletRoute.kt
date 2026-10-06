package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    WalletPage(state, query, collapsed, { query = it }, { id -> collapsed = if (id in collapsed) collapsed - id else collapsed + id },
        open, signIn = { signingIn = true }, retry = { model.load() })
    if (signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInSheet(
        codeStep = auth.phase == AccountPhase.CODE_SENT || auth.phase == AccountPhase.VERIFYING,
        busy = auth.phase == AccountPhase.SENDING_CODE || auth.phase == AccountPhase.VERIFYING,
        email = auth.email, message = auth.message, codeSentAt = auth.codeSentAt,
        requestCode = { scope.launch { account.requestCode(it) } }, verify = { scope.launch { account.verify(it) } },
        changeEmail = account::changeEmail, close = { signingIn = false },
    )
}
