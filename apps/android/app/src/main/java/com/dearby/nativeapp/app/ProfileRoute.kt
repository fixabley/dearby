package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.account.SignInSheet
import com.dearby.nativeapp.pages.profile.ProfileEditPage
import com.dearby.nativeapp.pages.profile.ProfilePage
import kotlinx.coroutines.launch

/** 내 프로필 tab: loads on entry and after signing in; card making stays available signed out. */
@Composable fun ProfileRoute(model: ProfileViewModel, account: AccountViewModel, compose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val edit by model.edit.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    edit?.let { current ->
        BackHandler(onBack = model::close)
        ProfileEditPage(current.form, current.errors, current.saving, current.error, model::change, model::addContact, model::addHistory, model::save, model::close)
    } ?: ProfilePage(state, model::startEdit, compose, { signingIn = true }, { model.load() }) { openContact(context, it) }
    if (signingIn && !signedIn) SignInSheet(auth.phase == AccountPhase.WORKING, auth.message,
        signIn = { scope.launch { account.signIn(Passkeys(context)) } }, signUp = { scope.launch { account.signUp(Passkeys(context)) } },
        close = { signingIn = false },
    )
}
