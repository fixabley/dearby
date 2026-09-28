package com.dearby.nativeapp.app.providers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.app.DearbyViewModel
import com.dearby.nativeapp.entities.profile.api.ProfileRepository
import com.dearby.nativeapp.entities.card.api.CardRepository
import com.dearby.nativeapp.features.wallet.WalletRepository
import com.dearby.nativeapp.features.account.AuthRepository
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.storage.DearbyDatabase
import com.dearby.nativeapp.shared.storage.TokenVault

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    private val database by lazy { Room.databaseBuilder(context, DearbyDatabase::class.java, "dearby.db").build() }
    private val vault by lazy { TokenVault(context) }
    private val http by lazy { HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG, vault::read) }
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == DearbyViewModel::class.java || modelClass == com.dearby.nativeapp.app.CatalogViewModel::class.java)
        val dao = database.dao()
        if (modelClass == com.dearby.nativeapp.app.CatalogViewModel::class.java) return com.dearby.nativeapp.app.CatalogViewModel(com.dearby.nativeapp.entities.catalog.api.CatalogRepository(http, dao)) as T
        return DearbyViewModel(ProfileRepository(http, dao), CardRepository(http, dao), WalletRepository(http, dao), AuthRepository(http), GuestStore(dao), vault) as T
    }
}
