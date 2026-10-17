package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.apple.AppleBrowser
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.account.AppleAccountSheet
import com.dearby.nativeapp.pages.account.SignInSheet
import kotlinx.coroutines.launch

/** The sign-in sheet every screen that needs an account opens. While Apple's choice is pending [AppleAccountRoute] shows instead. */
@Composable fun SignInRoute(account: AccountViewModel, close: () -> Unit) {
    val auth by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (auth.appleEmail == null) SignInSheet(auth.phase == AccountPhase.WORKING, auth.message,
        signIn = { scope.launch { account.signIn(Passkeys(context)) } }, apple = { scope.launch { account.startApple(AppleBrowser(context)) } },
        signUp = { scope.launch { account.signUp(Passkeys(context)) } }, close = close,
    )
}

/**
 * The 202 follow-up, drawn by MainActivity above every screen: Apple's answer arrives as a link, maybe after the screen that
 * asked was recreated, so it cannot hang off that screen's sheet. First the device-passkey attempt, then the choice.
 */
@Composable fun AppleAccountRoute(account: AccountViewModel) {
    val auth by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val email = auth.appleEmail ?: return
    if (auth.appleAuto) {
        // The device-passkey attempt runs first; the choice below is drawn only if it was closed.
        LaunchedEffect(email) { account.autoLinkApple(Passkeys(context)) }
        return
    }
    AppleAccountSheet(email, auth.phase == AccountPhase.WORKING, auth.message,
        startNew = { scope.launch { account.registerApple() } }, linkPasskey = { scope.launch { account.linkAppleWithPasskey(Passkeys(context)) } },
        close = account::dismissApple,
    )
}
