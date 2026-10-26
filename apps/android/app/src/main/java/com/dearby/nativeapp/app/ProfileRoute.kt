package com.dearby.nativeapp.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.PickVisualMediaRequest
import com.dearby.nativeapp.features.photo.PhotoCropPage
import com.dearby.nativeapp.features.photo.decodeForCrop
import com.dearby.nativeapp.features.photo.squareJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dearby.nativeapp.features.push.Push
import com.dearby.nativeapp.pages.profile.NotificationRow
import com.dearby.nativeapp.pages.profile.UsageStatsRow
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.pages.profile.ConsentRequiredMessage
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.shared.ui.TabActions
import androidx.compose.foundation.layout.fillMaxWidth
import com.dearby.nativeapp.pages.profile.HistoryFunnelPage
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.pages.profile.ProfilePage
import com.dearby.nativeapp.pages.profile.ProfilePhase
import com.dearby.nativeapp.pages.profile.ProfileVersionPage
import com.dearby.nativeapp.pages.profile.ProfileVersionsPage
import androidx.compose.material3.*
import com.dearby.nativeapp.pages.recommendation.EligibilityActionsSheet
import com.dearby.nativeapp.pages.recommendation.EligibilityConsentSheet
import com.dearby.nativeapp.pages.recommendation.EligibilityFormPage
import com.dearby.nativeapp.widgets.recommendation.recommendationRows.RecommendationProfileGroup

/** 내 프로필 tab: read mode by default with the top-bar 수정 editor (contract "프로필 읽기/수정 모드"), the history funnel (with file pickers for evidence) and the version screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProfileRoute(model: ProfileViewModel, account: AccountViewModel, recommendation: RecommendationViewModel, compose: () -> Unit, settings: () -> Unit = {}, notify: () -> Unit = {}, leaveSetup: () -> Unit = {}, askConsent: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val funnel by model.funnel.collectAsStateWithLifecycle()
    val versions by model.versions.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var signingIn by remember { mutableStateOf(false) }
    var versionsOpen by remember { mutableStateOf(false) }
    var openVersion by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val openContact = rememberOpenContact()
    // 연락처 인증 notices (contract "연락처 인증") go to the shell's Snackbar, once each.
    val showNotice = LocalNotice.current
    LaunchedEffect(state.notice) { state.notice?.let { showNotice(it); model.dismissNotice() } }
    // 지원 자격 정보 (contract "지원 자격 정보(프로필, 동의 필요)", signed in only): no current consent → the consent sheet; entered → 고치기·지우기.
    val reco by recommendation.state.collectAsStateWithLifecycle()
    val eligibilityForm by recommendation.form.collectAsStateWithLifecycle()
    var eligibilitySheet by remember { mutableStateOf<EligibilitySheet?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val openEligibility = {
        Tracking.menu("eligibility")
        eligibilitySheet = null
        when {
            !reco.eligibilityConsented -> eligibilitySheet = EligibilitySheet.CONSENT
            reco.eligibilitySummary != null -> eligibilitySheet = EligibilitySheet.ACTIONS
            else -> recommendation.startEligibility()
        }
    }
    // Signed out there is no recommendation, so no 추천 묶음 (contract "비로그인에서 만들기·추천을 막음", 2026-10-25); signed in it waits for the recommendation API.
    val recommendationGroup: (@Composable () -> Unit)? = if (!signedIn || !reco.available) null else {
        { RecommendationProfileGroup(reco.eligibilitySummary, { Tracking.menu("interests"); recommendation.openFunnel(first = false, entry = "profile") }, openEligibility) }
    }
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
    // 프로필 사진 (contract "프로필 사진"): the system Photo Picker (no permission), then the crop screen with the upright, downsized photo.
    val scope = rememberCoroutineScope()
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        model.dismissPhotoError()
        preparing = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeForCrop(resolver, uri) }
            preparing = false
            if (bitmap == null) model.photoFailed("이 사진은 열 수 없어요. 다른 사진을 골라 주세요.") else cropping = bitmap
        }
    }
    when {
        eligibilityForm != null -> eligibilityForm?.let { form ->
            BackHandler(onBack = recommendation::formBack)
            EligibilityFormPage(form, recommendation::choose, recommendation::chooseYear, recommendation::birthYear, recommendation::formNext, recommendation::formBack)
        }
        // 개인정보 동의 (contract "개인정보 동의" 2026-10-25): signed in without it the tab shows the gate; the consent screen is the app's route.
        signedIn && state.phase == ProfilePhase.LOADED && state.consentRequired -> Column {
            DearbyTopBar("내 프로필") { TabActions(settings, notify) }
            ConsentRequiredMessage(askConsent, Modifier.weight(1f).fillMaxWidth())
        }
        cropping != null -> cropping?.let { bitmap ->
            PhotoCropPage(bitmap, state.photoBusy, state.photoError, cancel = { cropping = null; model.dismissPhotoError() }) { square ->
                scope.launch {
                    val jpeg = withContext(Dispatchers.Default) { squareJpeg(bitmap, square) }
                    model.uploadPhoto(jpeg) { cropping = null }
                }
            }
        }
        funnel != null -> funnel?.let { current ->
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
        else -> ProfilePage(state, model.rows, model::change, model::searchJobs, model::addContact, model::openHistory, model::removeHistory, { if (state.consentRequired) askConsent() else model.save() }, model::revert,
            { Tracking.menu("history"); versionsOpen = true }, compose, { signingIn = true }, { model.load() }, model.changed, { if (it) Tracking.menu("profile_edit"); model.edit(it) }, settings, notify, recommendationGroup, leaveSetup,
            pickPhoto = { if (state.consentRequired) askConsent() else if (!preparing) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            deletePhoto = { if (state.consentRequired) askConsent() else model.deletePhoto() }, contact = openContact,
            linkGithub = { if (state.consentRequired) askConsent() else model.linkGithub(OAuthBrowser(context)) }, requestEmailCode = { if (state.consentRequired) askConsent() else model.requestEmailCode() },
            changeEmailCode = model::changeEmailCode, verifyEmail = model::verifyEmail)
    }
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
    eligibilitySheet?.let { sheet ->
        ModalBottomSheet({ eligibilitySheet = null }, containerColor = MaterialTheme.colorScheme.surface) {
            when (sheet) {
                EligibilitySheet.CONSENT -> EligibilityConsentSheet(agree = { eligibilitySheet = null; recommendation.startEligibility() },
                    openTerms = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }, close = { eligibilitySheet = null })
                EligibilitySheet.ACTIONS -> EligibilityActionsSheet(reco.eligibilitySummary.orEmpty(), edit = { eligibilitySheet = null; recommendation.startEligibility() },
                    delete = { eligibilitySheet = null; confirmDelete = true })
            }
        }
    }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("지원 자격 정보를 지울까요?") }, text = { Text("입력한 정보와 동의 기록이 바로 삭제돼요.") },
        confirmButton = { TextButton({ confirmDelete = false; recommendation.deleteEligibility() }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("취소") } })
    reco.eligibilityError?.let { value -> AlertDialog(onDismissRequest = recommendation::dismissNotice, title = { Text("지원 자격 정보") }, text = { Text(value) }, confirmButton = { TextButton(recommendation::dismissNotice) { Text("확인") } }) }
}

private enum class EligibilitySheet { CONSENT, ACTIONS }
