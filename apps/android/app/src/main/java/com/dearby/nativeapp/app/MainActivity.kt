package com.dearby.nativeapp.app

import android.content.Intent
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.SessionVault
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.entities.activity.api.fetchActivities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.shared.config.appleRedirect
import com.dearby.nativeapp.shared.config.sharedCardId
import com.dearby.nativeapp.shared.config.sharedCardUrl
import com.dearby.nativeapp.shared.ui.DearbyTheme

class MainActivity : ComponentActivity() {
    private val activityModel: ActivityViewModel by viewModels {
        viewModelFactory { initializer { ActivityViewModel { query, cursor -> withContext(Dispatchers.IO) { fetchActivities(ApiOrigin.current, query, cursor) } } } }
    }
    private val account: AccountViewModel by viewModels {
        viewModelFactory { initializer { AccountViewModel(AccountClient(ApiOrigin.current), SessionVault(applicationContext, ApiOrigin.sessionName), createSavedStateHandle()) } }
    }
    private val publish: CardPublishViewModel by viewModels { viewModelFactory { initializer { CardPublishViewModel(account) } } }
    private val wallet: WalletViewModel by viewModels { viewModelFactory { initializer { WalletViewModel(account) } } }
    private val profile: ProfileViewModel by viewModels { viewModelFactory { initializer { ProfileViewModel(account) } } }
    private val qrShare: QrShareViewModel by viewModels {
        viewModelFactory { initializer { QrShareViewModel(account) { sharedCardUrl(it, BuildConfig.WEB_ORIGIN) } } }
    }
    // Captured from /s/<UUID> App Links and shown as the shared-card screen.
    private var incoming by mutableStateOf<ScannedLink?>(null)
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
        // Someone already signed in (tokens in the vault) never needs the intro.
        if (!onboarding.getBoolean("dearby.onboarding.shown", false)) {
            if (account.state.value.phase == AccountPhase.SIGNED_IN) introShown() else intro = true
        }
        // A Debug build without DEARBY_API_ORIGIN talks to the emulator host's local API; say so once.
        if (BuildConfig.DEBUG && ApiOrigin.current.startsWith("http://10.0.2.2")) {
            Log.w("Dearby", "Debug build uses the local API fallback ${ApiOrigin.current}; set DEARBY_API_ORIGIN when building to use another API.")
        }
        // A recreated Activity gets its launch intent again; Apple's code is single-use and was handled the first time.
        receive(intent, recreated = savedInstanceState != null)
        setContent {
            DearbyTheme {
                if (intro) IntroRoute(account, ::introShown)
                else DearbyApp(activityModel, account, publish, qrShare, wallet, profile, incoming) { incoming = null }
                AppleAccountRoute(account)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }
    /** `/s/<UUID>` App Links, and Apple's `dearby://auth/apple` redirect (singleTask brings this instance back over the Custom Tab). */
    private fun receive(intent: Intent?, recreated: Boolean = false) {
        val link = intent?.dataString ?: return
        appleRedirect(link)?.let { if (!recreated) account.appleReturned(it); return }
        sharedCardId(link, BuildConfig.WEB_ORIGIN)?.let { incoming = ScannedLink.Share(it) }
    }
}
