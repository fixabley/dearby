package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.profile.ProfileEditPage
import com.dearby.nativeapp.pages.profile.ProfilePage

/** 내 프로필 tab: loads on entry and after signing in; card making stays available signed out. */
@Composable fun ProfileRoute(model: ProfileViewModel, account: AccountViewModel, compose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val edit by model.edit.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val deletion by model.deletion.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var signingIn by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    edit?.let { current ->
        BackHandler(onBack = model::close)
        ProfileEditPage(current.form, current.errors, current.saving, current.error, model::change, model::addContact, model::addHistory, model::save, model::close)
    } ?: ProfilePage(state, model::startEdit, compose, { signingIn = true }, { model.load() }, { openContact(context, it) }, deletion.deleting, model::deleteAccount)
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
    deletion.notice?.let { notice ->
        AlertDialog(onDismissRequest = model::dismissDeletionNotice, text = { Text(notice) }, confirmButton = { TextButton(model::dismissDeletionNotice) { Text("확인") } })
    }
}
