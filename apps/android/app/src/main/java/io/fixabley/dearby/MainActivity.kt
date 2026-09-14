package io.fixabley.dearby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.core.data.AssetCatalogProvider
import io.fixabley.dearby.core.data.SharedPreferencesFavoriteStore
import io.fixabley.dearby.core.state.FavoritesState
import io.fixabley.dearby.ui.theme.DearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val catalogProvider = AssetCatalogProvider(applicationContext.assets)
        val favorites = FavoritesState(SharedPreferencesFavoriteStore(
            getSharedPreferences(SharedPreferencesFavoriteStore.FILE_NAME, MODE_PRIVATE)
        ))
        setContent {
            DearbyTheme {
                DearbyApp(catalogProvider, favorites, onOpenSource = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { Toast.makeText(this, "공고를 열 브라우저가 없어요", Toast.LENGTH_SHORT).show() }
                })
            }
        }
    }
}
