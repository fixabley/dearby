package com.dearby.nativeapp.app

import android.content.Intent
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.SessionVault
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.entities.catalog.api.fetchCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.shared.config.sharedCardId
import com.dearby.nativeapp.shared.config.sharedCardUrl
import com.dearby.nativeapp.shared.ui.DearbyTheme

class MainActivity : ComponentActivity() {
    private val catalog: CatalogViewModel by viewModels {
        viewModelFactory { initializer { CatalogViewModel { withContext(Dispatchers.IO) { fetchCatalog(ApiOrigin.current) } } } }
    }
    private val account: AccountViewModel by viewModels {
        viewModelFactory { initializer { AccountViewModel(AccountClient(ApiOrigin.current), SessionVault(applicationContext, ApiOrigin.sessionName)) } }
    }
    private val publish: CardPublishViewModel by viewModels { viewModelFactory { initializer { CardPublishViewModel(account) } } }
    private val wallet: WalletViewModel by viewModels { viewModelFactory { initializer { WalletViewModel(account) } } }
    private val profile: ProfileViewModel by viewModels { viewModelFactory { initializer { ProfileViewModel(account) } } }
    private val qrShare: QrShareViewModel by viewModels {
        viewModelFactory { initializer { QrShareViewModel(account) { sharedCardUrl(it, BuildConfig.WEB_ORIGIN) } } }
    }
    // Captured from /s/<UUID> App Links and shown as the shared-card screen.
    private var incoming by mutableStateOf<ScannedLink?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A Debug build without DEARBY_API_ORIGIN talks to the emulator host's local API; say so once.
        if (BuildConfig.DEBUG && ApiOrigin.current.startsWith("http://10.0.2.2")) {
            Log.w("Dearby", "Debug build uses the local API fallback ${ApiOrigin.current}; set DEARBY_API_ORIGIN when building to use another API.")
        }
        receive(intent)
        setContent {
            DearbyTheme {
                DearbyApp(catalog, account, publish, qrShare, wallet, profile, incoming) { incoming = null }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }
    private fun receive(intent: Intent?) {
        intent?.dataString?.let { link -> sharedCardId(link, BuildConfig.WEB_ORIGIN)?.let { incoming = ScannedLink.Share(it) } }
    }
}
