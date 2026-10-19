package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.google.GoogleSignIn
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.settings.SettingsPage
import com.dearby.nativeapp.shared.config.AuthProvider
import com.dearby.nativeapp.shared.config.ConsentVersions
import kotlinx.coroutines.launch

/**
 * 설정 (contract "설정"): loads on entry and again after signing in, a successful Apple/Google link ([AccountState.linked]) or signing out.
 * 연결하기 reuses the native sign-in entry points, which link while signed in; 계정 삭제 reuses the profile's deletion flow and notice;
 * 개인정보·약관 open in a Custom Tab.
 */
@Composable fun SettingsRoute(model: SettingsViewModel, account: AccountViewModel, profile: ProfileViewModel, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val deletion by profile.deletion.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var signingIn by remember { mutableStateOf(false) }
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    BackHandler(onBack = close)
    LaunchedEffect(signedIn, auth.linked) { if (signedIn) signingIn = false; model.load() }
    SettingsPage(state.copy(busy = state.busy || auth.linking || deletion.deleting), back = close, signIn = { signingIn = true }, signOut = model::signOut, deleteAccount = profile::deleteAccount, retry = model::load,
        link = { key ->
            scope.launch {
                when (AuthProvider.entries.first { it.path == key }) {
                    AuthProvider.APPLE -> account.startWeb(AuthProvider.APPLE, OAuthBrowser(context))
                    AuthProvider.GOOGLE -> account.signInWithGoogle(GoogleSignIn(context))
                }
            }
        },
        unlink = model::unlink, addPasskey = { model.addPasskey(Passkeys(context)) }, renamePasskey = model::renamePasskey, deletePasskey = model::deletePasskey,
        openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }, openTerms = { OAuthBrowser(context).open(ConsentVersions.termsUrl(BuildConfig.WEB_ORIGIN)) },
        dismissNotice = model::dismissNotice, googleAvailable = auth.googleAvailable)
    // Link failures and the deletion result are one-time notices of their own owners.
    auth.linkMessage?.let { AlertDialog(onDismissRequest = account::clearLinkMessage, text = { Text(it) }, confirmButton = { TextButton(account::clearLinkMessage) { Text("확인") } }) }
    deletion.notice?.let { AlertDialog(onDismissRequest = profile::dismissDeletionNotice, text = { Text(it) }, confirmButton = { TextButton(profile::dismissDeletionNotice) { Text("확인") } }) }
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
}
