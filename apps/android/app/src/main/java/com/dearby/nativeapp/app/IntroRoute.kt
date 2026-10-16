package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.apple.AppleBrowser
import com.dearby.nativeapp.pages.onboarding.IntroPage
import kotlinx.coroutines.launch

/** First-launch intro. Signing in (passkey or Apple) or "둘러보기" both end it; a closed or failed sign-in stays here. */
@Composable fun IntroRoute(account: AccountViewModel, done: () -> Unit) {
    val auth by account.state.collectAsStateWithLifecycle()
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    LaunchedEffect(signedIn) { if (signedIn) done() }
    IntroPage(start = { signingIn = true }, apple = { scope.launch { account.startApple(AppleBrowser(context)) } }, browse = done,
        busy = auth.phase == AccountPhase.WORKING, message = auth.message.takeIf { !signingIn })
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
}
