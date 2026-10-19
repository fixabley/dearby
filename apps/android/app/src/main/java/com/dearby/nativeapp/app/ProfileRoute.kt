package com.dearby.nativeapp.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

/** 내 프로필 tab: read mode by default with the top-bar 수정 editor (contract "프로필 읽기/수정 모드"), the history funnel (with file pickers for evidence) and the version screens. */
@Composable fun ProfileRoute(model: ProfileViewModel, account: AccountViewModel, compose: () -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val funnel by model.funnel.collectAsStateWithLifecycle()
    val versions by model.versions.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var signingIn by remember { mutableStateOf(false) }
    var versionsOpen by remember { mutableStateOf(false) }
    var openVersion by remember { mutableStateOf<Int?>(null) }
    // 동의 시트 닫기 (contract, 2026-10-20): closing the consent page is "not consented"; the next save attempt brings it back instead of a doomed PUT.
    var consentDismissed by remember { mutableStateOf(false) }
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
        signedIn && state.phase == ProfilePhase.LOADED && state.consentRequired && !consentDismissed -> {
            BackHandler { consentDismissed = true }
            ConsentPage(
                state.consenting, state.consentError,
                openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) },
                openTerms = { OAuthBrowser(context).open(ConsentVersions.termsUrl(BuildConfig.WEB_ORIGIN)) },
                agree = model::consent, close = { consentDismissed = true },
            )
        }
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
        else -> ProfilePage(state, model.rows, model::change, model::searchJobs, model::addContact, model::openHistory, model::removeHistory, { if (state.consentRequired) consentDismissed = false else model.save() }, model::revert,
            { versionsOpen = true }, compose, { signingIn = true }, { model.load() }, model.changed, model::edit, settings, notify)
    }
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
}
