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
import com.dearby.nativeapp.shared.ui.DearbyTheme

class MainActivity : ComponentActivity() {
    private val activityModel: ActivityViewModel by viewModels {
        viewModelFactory {
            initializer {
                ActivityViewModel(
                    { query, cursor, openOnly -> withContext(Dispatchers.IO) { fetchPrograms(ApiOrigin.current, query, null, cursor, openOnly) } },
                    { query, cursor, programId, open -> withContext(Dispatchers.IO) { fetchActivities(ApiOrigin.current, query, cursor, programId, open) } },
                    { id -> withContext(Dispatchers.IO) { fetchActivity(ApiOrigin.current, id) } },
                    account,
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
    private val settings: SettingsViewModel by viewModels { viewModelFactory { initializer { SettingsViewModel(account, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") } } }
    // 추천 설정 while signed out and the funnel's completion flag (contract "추천 설정 저장": the app's existing settings store, new keys); nothing older is read.
    private val recommendation: RecommendationViewModel by viewModels {
        viewModelFactory {
            initializer {
                RecommendationViewModel(account, object : RecommendationStore {
                    override fun preferences() = onboarding.getString(RECOMMENDATION_KEY, null)
                    override fun savePreferences(json: String) = onboarding.edit { putString(RECOMMENDATION_KEY, json) }
                    override fun completed() = onboarding.getBoolean(RECOMMENDATION_DONE_KEY, false)
                    override fun markCompleted() = onboarding.edit { putBoolean(RECOMMENDATION_DONE_KEY, true) }
                })
            }
        }
    }
    // 오늘의 맞춤 대기열 reactions while signed out (contract "맞춤 대기열": 신호는 기기에만), a new key in the same file.
    private val queue: QueueViewModel by viewModels {
        viewModelFactory {
            initializer {
                QueueViewModel(account, { recommendation.currentPreferences }, object : QueueStore {
                    override fun signals() = onboarding.getString(QUEUE_SIGNALS_KEY, null)
                    override fun saveSignals(json: String) = onboarding.edit { putString(QUEUE_SIGNALS_KEY, json) }
                })
            }
        }
    }
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
        setContent {
            DearbyTheme {
                if (intro) IntroRoute(account, ::introShown)
                else DearbyApp(activityModel, account, publish, myCards, wallet, profile, settings, recommendation, queue, screenshot)
                PendingAccountRoute(account)
            }
        }
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
        val link = intent?.dataString ?: return
        if (recreated) return
        authRedirect(link)?.let { account.returned(it); return }
        cardLink(link, BuildConfig.WEB_ORIGIN)?.let { if (it.web) wallet.view(it) else wallet.receive(it); return }
        activityAppLink(link, BuildConfig.WEB_ORIGIN)?.let(activityModel::openLinked)
    }
}

private const val RECOMMENDATION_KEY = "dearby.recommendation.preferences"
private const val RECOMMENDATION_DONE_KEY = "dearby.recommendation.completed"
private const val QUEUE_SIGNALS_KEY = "dearby.queue.signals"
