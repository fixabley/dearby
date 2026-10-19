package com.dearby.nativeapp.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.pages.profile.ConsentPage
import com.dearby.nativeapp.pages.profile.HistoryFunnelPage
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.pages.profile.ProfilePage
import com.dearby.nativeapp.pages.profile.ProfilePhase
import com.dearby.nativeapp.pages.profile.ProfileVersionPage
import com.dearby.nativeapp.pages.profile.ProfileVersionsPage

/** 내 프로필 tab: in-place data editing, the history funnel (with file pickers for evidence) and the version screens. */
@Composable fun ProfileRoute(model: ProfileViewModel, account: AccountViewModel, compose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val funnel by model.funnel.collectAsStateWithLifecycle()
    val versions by model.versions.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val deletion by model.deletion.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var signingIn by remember { mutableStateOf(false) }
    var versionsOpen by remember { mutableStateOf(false) }
    var openVersion by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    LaunchedEffect(signedIn) { if (signedIn) signingIn = false; model.load() }
    val resolver = context.contentResolver
    fun read(uri: Uri?, fallbackType: String, fallbackName: String) {
        uri ?: return
        val type = resolver.getType(uri) ?: fallbackType
        val name = runCatching { resolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull() ?: fallbackName
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return
        model.upload(name, type, bytes)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { read(it, "image/jpeg", "photo.jpg") }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { read(it, "application/pdf", "evidence.pdf") }
    when {
        // 개인정보 동의 (contract "개인정보 동의"): before the first profile step and whenever the server asks again; never when signed out.
        signedIn && state.phase == ProfilePhase.LOADED && state.consentRequired -> ConsentPage(
            state.consenting, state.consentError,
            openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) },
            openTerms = { OAuthBrowser(context).open(ConsentVersions.termsUrl(BuildConfig.WEB_ORIGIN)) },
            agree = model::consent,
        )
        funnel != null -> funnel?.let { current ->
            BackHandler(onBack = model::closeFunnel)
            HistoryFunnelPage(current, model::changeDraft, model::goTo, model::next, model::search,
                { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                { pickPdf.launch(arrayOf("application/pdf")) }, model::deleteEvidence, model::closeFunnel)
        }
        openVersion != null -> openVersion?.let { version ->
            BackHandler { openVersion = null; model.closeVersion() }
            LaunchedEffect(version) { model.openVersion(version) }
            ProfileVersionPage(versions, version, { model.restore(version) { openVersion = null; versionsOpen = false; model.closeVersion() } }) { openVersion = null; model.closeVersion() }
        }
        versionsOpen -> {
            BackHandler { versionsOpen = false }
            LaunchedEffect(Unit) { model.loadVersions(reset = true) }
            ProfileVersionsPage(versions, { openVersion = it }, { model.loadVersions(reset = false) }) { versionsOpen = false }
        }
        else -> ProfilePage(state, model.rows, model::change, model::searchJobs, model::addContact, model::openHistory, model::removeHistory, model::save, model::revert,
            { versionsOpen = true }, compose, { signingIn = true }, { model.load() }, deletion.deleting, model::deleteAccount, model.changed)
    }
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
    deletion.notice?.let { notice ->
        AlertDialog(onDismissRequest = model::dismissDeletionNotice, text = { Text(notice) }, confirmButton = { TextButton(model::dismissDeletionNotice) { Text("확인") } })
    }
}
