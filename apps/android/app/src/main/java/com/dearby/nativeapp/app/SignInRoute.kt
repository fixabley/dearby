package com.dearby.nativeapp.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.google.GoogleSignIn
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.account.LinkAccountSheet
import com.dearby.nativeapp.pages.account.SignInSheet
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.shared.config.AuthProvider
import com.dearby.nativeapp.shared.config.ConsentVersions
import kotlinx.coroutines.launch

/** The sign-in sheet every screen that needs an account opens. While a provider's choice is pending [PendingAccountRoute] shows instead. */
@Composable fun SignInRoute(account: AccountViewModel, close: () -> Unit) {
    val auth by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    if (auth.pending == null) SignInSheet(auth.phase == AccountPhase.WORKING, auth.message, ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN),
        signIn = { scope.launch { account.signIn(Passkeys(context)) } }, apple = { scope.launch { account.startWeb(AuthProvider.APPLE, OAuthBrowser(context)) } },
        google = if (auth.googleAvailable) ({ scope.launch { account.signInWithGoogle(GoogleSignIn(context)) } }) else null,
        close = close,
    )
}

/**
 * The 202 follow-up, drawn by MainActivity above every screen: the provider's answer arrives as a link, maybe after the screen that
 * asked was recreated, so it cannot hang off that screen's sheet. First the device-passkey attempt, then the choice.
 */
@Composable fun PendingAccountRoute(account: AccountViewModel) {
    val auth by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pending = auth.pending ?: return
    if (pending.auto) {
        // The device-passkey attempt runs first; the choice below is drawn only if it was closed.
        LaunchedEffect(pending.provider, pending.email) { account.autoLinkPending(Passkeys(context)) }
        return
    }
    LinkAccountSheet(pending.provider.label, pending.email, auth.phase == AccountPhase.WORKING, auth.message,
        startNew = { scope.launch { account.registerPending() } }, linkPasskey = { scope.launch { account.linkPendingWithPasskey(Passkeys(context)) } },
        close = account::dismissPending,
    )
}
