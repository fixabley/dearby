package com.dearby.nativeapp.app

import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.net.toUri
import com.dearby.nativeapp.features.push.Push
import com.dearby.nativeapp.pages.profile.NotificationRow
import com.dearby.nativeapp.features.live.LiveSessions
import com.dearby.nativeapp.features.live.LiveDisplaySheet
import com.dearby.nativeapp.features.live.LiveDisplayState
import com.dearby.nativeapp.pages.profile.LiveDisplayRow
import com.dearby.nativeapp.pages.profile.LiveSessionPreviewRow
import com.dearby.nativeapp.pages.profile.SessionReminderRow
import com.dearby.nativeapp.features.reminder.SessionReminders
import com.dearby.nativeapp.pages.profile.UsageStatsRow
import com.dearby.nativeapp.pages.profile.AdConsentRow
import com.dearby.nativeapp.features.ads.AdConsentSheet
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/**
 * 설정 (contract "설정"): loads on entry and again after signing in, a successful Apple/Google link ([AccountState.linked]) or signing out.
 * 연결하기 reuses the native sign-in entry points, which link while signed in; 계정 삭제 reuses the profile's deletion flow and notice;
 * 개인정보·약관 open in a Custom Tab.
 */
@Composable fun SettingsRoute(model: SettingsViewModel, account: AccountViewModel, profile: ProfileViewModel, notifications: NotificationViewModel?, analytics: Boolean, setAnalytics: (Boolean) -> Unit,
    /** 세션 이동 알림 (device-local, 2026-10-26), shown with the account's notification rows. */
    sessionReminders: Boolean, setSessionReminders: (Boolean) -> Unit,
    /** Debug only: 오늘 행사 온보딩's fake-day switch (2026-10-27); null hides it. */
    todayOnboarding: TodayOnboardingViewModel? = null, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val deletion by profile.deletion.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var signingIn by remember { mutableStateOf(false) }
    var askingAd by remember { mutableStateOf(false) }
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    BackHandler(onBack = close)
    // The system notification switch is read again whenever the app comes back (e.g. from its settings).
    var systemOn by remember { mutableStateOf(true) }
    var exactAlarms by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) { systemOn = Push.enabled(context); exactAlarms = SessionReminders.exact(context); onPauseOrDispose {} }
    LaunchedEffect(signedIn, auth.linked) { if (signedIn) signingIn = false; model.load() }
    SettingsPage(state.copy(busy = state.busy || auth.linking || deletion.deleting), back = close, signIn = { signingIn = true }, signOut = model::signOut, deleteAccount = { profile.deleteAccount(state.passkeys.isNotEmpty()) }, retry = model::load,
        link = { key ->
            scope.launch {
                when (AuthProvider.entries.first { it.path == key }) {
                    AuthProvider.APPLE -> account.startWeb(AuthProvider.APPLE, OAuthBrowser(context))
                    AuthProvider.GOOGLE -> account.signInWithGoogle(GoogleSignIn(context))
                }
            }
        },
        unlink = model::unlink, addPasskey = { model.addPasskey(Passkeys(context)) }, renamePasskey = model::renamePasskey, deletePasskey = model::deletePasskey, unblock = model::unblock,
        openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }, openTerms = { OAuthBrowser(context).open(ConsentVersions.termsUrl(BuildConfig.WEB_ORIGIN)) },
        dismissNotice = model::dismissNotice, googleAvailable = auth.googleAvailable,
        notifications = if (notifications == null && !BuildConfig.DEBUG) null else { {
            notifications?.let { push ->
                val notice by push.state.collectAsStateWithLifecycle()
                NotificationRow(notice.newRecruitment, notice.saving, !systemOn, push::setNewRecruitment) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                }
                notice.message?.let { value -> AlertDialog(onDismissRequest = push::dismissMessage, title = { Text("알림") }, text = { Text(value) }, confirmButton = { TextButton(push::dismissMessage) { Text("확인") } }) }
                // Android 12+ `알람 및 리마인더` (SCHEDULE_EXACT_ALARM) for this app; the setting intent exists from that version on.
                SessionReminderRow(sessionReminders, !exactAlarms, setSessionReminders) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())) }
                }
                var display by remember { mutableStateOf<LiveDisplayState?>(null) }
                LiveDisplayRow { display = LiveDisplayState.of(context) }
                display?.let { LiveDisplaySheet(it, { openLiveDisplay(context, LiveDisplayState.promotionSettings(context)) }, { openLiveDisplay(context, LiveDisplayState.nowBarSettings) }, "닫기") { display = null } }
            }
            // Debug only (2026-10-27 user decision): a fake 행사 진행 중 notification on demand, also signed out, so the live update can be checked on any device.
            if (BuildConfig.DEBUG) {
                var moving by remember { mutableStateOf(LiveSessions.previewMoving) }
                LiveSessionPreviewRow(moving) {
                    moving = when (moving) { null -> false; false -> true; true -> null }; LiveSessions.previewMoving = moving
                    LiveSessions.apply(context, moving?.let { LiveSessions.preview(it) })
                }
            }
        } },
        privacy = {
            UsageStatsRow(analytics, setAnalytics)
            state.adConsent?.let { on -> AdConsentRow(on, !state.busy) { if (it) askingAd = true else model.setAdConsent(false) } }
            // Debug only (2026-10-27): 오늘 행사 온보딩 on a day without an event — the nearest coming event day stands in for today, asked again at once.
            if (BuildConfig.DEBUG && todayOnboarding != null) {
                var fake by remember { mutableStateOf(todayOnboarding.fake) }
                ListItem({ Text("오늘 행사 온보딩 가짜 날짜") }, Modifier.toggleable(fake, role = Role.Switch) { fake = it; todayOnboarding.setFake(it) },
                    supportingContent = { Text("가장 가까운 행사 날을 오늘로 보고 설정을 닫으면 다시 물어요 (Debug)") }, trailingContent = { Switch(fake, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        })
    if (askingAd) AdConsentSheet({ askingAd = false; model.setAdConsent(true) }, { askingAd = false }) { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }
    // Link failures and the deletion result are one-time notices of their own owners.
    auth.linkMessage?.let { AlertDialog(onDismissRequest = account::clearLinkMessage, text = { Text(it) }, confirmButton = { TextButton(account::clearLinkMessage) { Text("확인") } }) }
    deletion.notice?.let { AlertDialog(onDismissRequest = profile::dismissDeletionNotice, text = { Text(it) }, confirmButton = { TextButton(profile::dismissDeletionNotice) { Text("확인") } }) }
    if (signingIn && !signedIn) SignInRoute(account, close = { signingIn = false })
}
