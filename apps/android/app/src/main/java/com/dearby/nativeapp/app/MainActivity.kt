package com.dearby.nativeapp.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.entities.profile.api.ProfileRepository
import com.dearby.nativeapp.entities.card.api.CardRepository
import com.dearby.nativeapp.features.wallet.WalletRepository
import com.dearby.nativeapp.features.account.AuthRepository
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.storage.*
import com.dearby.nativeapp.shared.ui.DearbyTheme
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)
    private val model: DearbyViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = Room.databaseBuilder(applicationContext, DearbyDatabase::class.java, "dearby.db").build()
                val vault = TokenVault(applicationContext)
                val http = HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, vault::read)
                val dao = database.dao()
                return DearbyViewModel(ProfileRepository(http, dao), CardRepository(http, dao), WalletRepository(http, dao), AuthRepository(http, dao), GuestStore(dao), vault) as T
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming.value = intent?.dataString
        setContent { DearbyTheme { DearbyApp(model, incoming.value) { incoming.value = null } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming.value = intent.dataString }
}
