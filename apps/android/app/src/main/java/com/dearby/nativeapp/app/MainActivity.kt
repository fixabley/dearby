package com.dearby.nativeapp.app

import android.content.Intent
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.pages.activity.DiscoveryKind
import com.dearby.nativeapp.pages.activity.StatusGroup
import com.dearby.nativeapp.entities.account.api.SessionVault
import com.dearby.nativeapp.entities.activity.api.fetchActivities
import com.dearby.nativeapp.entities.activity.api.fetchActivity
import com.dearby.nativeapp.entities.activity.api.fetchPrograms
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.shared.config.activityAppLink
import com.dearby.nativeapp.shared.config.authRedirect
import com.dearby.nativeapp.shared.config.cardLink
import com.dearby.nativeapp.shared.config.isFavoritesLink
import com.dearby.nativeapp.features.push.Push
import com.dearby.nativeapp.features.ads.QueueAd
import com.dearby.nativeapp.features.analytics.Analytics
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.dearby.nativeapp.shared.ui.DearbyTheme
import com.dearby.nativeapp.shared.ui.LocalPhotoLoader
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    private val activityModel: ActivityViewModel by viewModels {
        viewModelFactory {
            initializer {
                ActivityViewModel(
                    { query, cursor, status, kind, category -> withContext(Dispatchers.IO) { fetchPrograms(ApiOrigin.current, query, null, cursor, status, kind, category) } },
                    { query, cursor, programId, status, kind, category -> withContext(Dispatchers.IO) { fetchActivities(ApiOrigin.current, query, cursor, programId, status, kind, category) } },
                    { id -> withContext(Dispatchers.IO) { fetchActivity(ApiOrigin.current, id) } },
                    account,
                    DiscoveryKind.of(device.discoveryKind), { device.discoveryKind = it.code },
                    StatusGroup.of(device.discoveryStatus), { device.discoveryStatus = StatusGroup.codes(it) },
                    { device.discoveryCategories(it.code) }, { kind, codes -> device.setDiscoveryCategories(kind.code, codes) },
                    device::autopassAsked, device::rememberAutopassAsked,
                )
            }
        }
    }
    private val account: AccountViewModel by viewModels {
        viewModelFactory { initializer { AccountViewModel(AccountClient(ApiOrigin.current), SessionVault(applicationContext, ApiOrigin.sessionName), createSavedStateHandle(), BuildConfig.GOOGLE_SERVER_CLIENT_ID) } }
    }
    private val publish: CardPublishViewModel by viewModels { viewModelFactory { initializer { CardPublishViewModel(account) } } }
    private val wallet: WalletViewModel by viewModels {
        viewModelFactory {
            initializer {
                // 공유받은 명함 보기's `<프로그램> <활동>` line, like the web card page: a gone or unreadable activity leaves it out.
                WalletViewModel(account) { id ->
                    try { withContext(Dispatchers.IO) { fetchActivity(ApiOrigin.current, id.toString()) }?.let { "${it.program} ${it.name}" } }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                }
            }
        }
    }
    private val profile: ProfileViewModel by viewModels { viewModelFactory { initializer { ProfileViewModel(account) } } }
    private val myCards: MyCardsViewModel by viewModels { viewModelFactory { initializer { MyCardsViewModel(account) } } }
    private val settings: SettingsViewModel by viewModels { viewModelFactory { initializer { SettingsViewModel(account, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", device) } } }
    // 추천 and 오늘의 맞춤 대기열 need a session (contract "비로그인에서 만들기·추천을 막음", 2026-10-25): nothing is kept on the device, and the
    // earlier keys (signed-out settings, queue reactions, the funnel's completion flag) are neither read nor removed.
    private val recommendation: RecommendationViewModel by viewModels { viewModelFactory { initializer { RecommendationViewModel(account) } } }
    private val queue: QueueViewModel by viewModels { viewModelFactory { initializer { QueueViewModel(account, QueueAd(applicationContext), device) } } }
    private val inbox: InboxViewModel by viewModels { viewModelFactory { initializer { InboxViewModel(account) } } }
    // 즐겨찾기·새 모집 푸시 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25): the asked-once flag in a new preferences file.
    private val device by lazy { DevicePreferences(applicationContext) }
    private val favorites: FavoritesViewModel by viewModels {
        viewModelFactory { initializer { FavoritesViewModel(account, activityModel::adopt) } }
    }
    private val sessions: SessionFavoritesViewModel by viewModels { viewModelFactory { initializer { SessionFavoritesViewModel(account) } } }
    private val notifications: NotificationViewModel by viewModels {
        viewModelFactory { initializer { NotificationViewModel(account, Push.available(applicationContext), { Push.token(applicationContext) }, Push.refreshed,
            { device.pushStarted }, { device.pushStarted = true; Push.enable(applicationContext) }) } }
    }
    // `오늘 일정 n개` opens 즐겨찾기 (dearby://favorites), consumed once by DearbyApp.
    private var favoritesLink by mutableStateOf(false)
    // The server sends at 8 in the morning in the device's time zone, so a change is sent again while the app runs (and at every start).
    private val timeZoneChanged = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) = notifications.register() }
    // First-launch intro, remembered in its own new preferences file; no earlier stored data is read.
    private val onboarding by lazy { getSharedPreferences("dearby.onboarding", MODE_PRIVATE) }
    private var intro by mutableStateOf(false)
    private fun introShown() {
        onboarding.edit { putBoolean("dearby.onboarding.shown", true) }
        intro = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // System bars follow the theme (contract "다크 모드"): transparent bars whose icons flip with light/dark; DearbyApp pads the insets once.
        enableEdgeToEdge()
        val screenshot = if (BuildConfig.DEBUG) intent?.getStringExtra(SCREENSHOT_EXTRA) else null
        if (screenshot != null) introShown()
        // Someone already signed in (tokens in the vault) never needs the intro.
        if (!onboarding.getBoolean("dearby.onboarding.shown", false)) {
            if (account.state.value.phase == AccountPhase.SIGNED_IN) introShown() else intro = true
        }
        // A Debug build without DEARBY_API_ORIGIN talks to the emulator host's local API; say so once.
        if (BuildConfig.DEBUG && ApiOrigin.current.startsWith("http://10.0.2.2")) {
            Log.w("Dearby", "Debug build uses the local API fallback ${ApiOrigin.current}; set DEARBY_API_ORIGIN when building to use another API.")
        }
        // A recreated Activity gets its launch intent again; the provider's code is single-use and was handled the first time.
        receive(intent, recreated = savedInstanceState != null)
        Push.createChannel(this)
        Analytics.start(applicationContext, device.analyticsEnabled)
        account.beforeSignOut = notifications::unregister
        ContextCompat.registerReceiver(this, timeZoneChanged, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            // Photos (contract "프로필 사진") need the session, so every avatar loads through the account's store.
            DearbyTheme { CompositionLocalProvider(LocalPhotoLoader provides account.photos::load) {
                if (intro) IntroRoute(account, ::introShown)
                else DearbyApp(activityModel, account, publish, myCards, wallet, profile, settings, recommendation, queue, favorites, notifications, inbox, device,
                    sessions, favoritesLink, { favoritesLink = false }, screenshot)
                PendingAccountRoute(account)
            } }
        }
    }
    override fun onDestroy() {
        unregisterReceiver(timeZoneChanged)
        super.onDestroy()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }
    /**
     * Apple's `dearby://auth/apple` redirect (singleTask brings this instance back over the Custom Tab), a received card
     * `dearby://cards/<id>?activity=<n>` from the web card page's save button (contract "명함 교환"; saved at once), or an App Link (contract
     * "유니버설 링크·App Links·활동 공유·오픈그래프"): the same card page, which opens the 받은 명함 detail or 공유받은 명함 보기 without saving, or
     * `/activities/<id>`, which opens the detail on 발견. Other links only open the app.
     */
    private fun receive(intent: Intent?, recreated: Boolean = false) {
        // A push tapped while the app was in the background starts it with the message's `data` as extras (`url`, contract "즐겨찾기(하트)·새 모집 푸시 알림").
        intent ?: return
        val link = intent.dataString ?: intent.getStringExtra("url") ?: return
        if (recreated) return
        // Opened from a push (analytics-v1 `push_open`); the server's `kind` when it sends one, the summary by its favorites link.
        val fromPush = intent.getBooleanExtra(Push.OPENED, false) || (intent.dataString == null && intent.hasExtra("url"))
        if (fromPush) {
            val kind = intent.getStringExtra("kind")?.lowercase() ?: if (isFavoritesLink(link)) "summary" else null
            Analytics.event("push_open", *listOfNotNull(kind?.let { "kind" to it }).toTypedArray())
            intent.getStringExtra("notificationId")?.let(inbox::read)
        }
        if (isFavoritesLink(link)) { favoritesLink = true; return }
        authRedirect(link)?.let { account.returned(it); return }
        cardLink(link, BuildConfig.WEB_ORIGIN)?.let { if (it.web) wallet.view(it) else wallet.receive(it); return }
        activityAppLink(link, BuildConfig.WEB_ORIGIN)?.let { activityModel.openLinked(it, if (fromPush) "push" else "link") }
    }
}
