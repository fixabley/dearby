package io.fixabley.dearby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.app.openCalendarEditor
import io.fixabley.dearby.app.openVenueMap
import io.fixabley.dearby.app.data.cache.NoticeCacheDatabase
import io.fixabley.dearby.app.data.cache.RoomSnapshotStore
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.features.favoriteorganization.api.SharedPreferencesFavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.shared.ui.theme.DearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val favorites = FavoritesState(SharedPreferencesFavoriteStore(
            getSharedPreferences(SharedPreferencesFavoriteStore.FILE_NAME, MODE_PRIVATE)
        ))
        val catalogProvider = NoticeSession(AssetNoticeSnapshotReader(applicationContext.assets), favorites) { snapshot, check ->
            val database = NoticeCacheDatabase.open(applicationContext)
            try { RoomSnapshotStore(database).prepare(snapshot, checkActive = check) }
            finally { database.close() }
        }
        val busyProvider = io.fixabley.dearby.features.calendarbusy.api.AndroidBusyProvider(applicationContext)
        val calendarSettings = io.fixabley.dearby.app.CalendarSettingsController(busyProvider,
            io.fixabley.dearby.app.SharedPreferencesCalendarSettingsStore(getSharedPreferences(
                io.fixabley.dearby.app.SharedPreferencesCalendarSettingsStore.FILE_NAME, MODE_PRIVATE)))
        setContent {
            DearbyTheme {
                DearbyApp(catalogProvider, busyProvider = busyProvider, calendarSettings = calendarSettings, onOpenSource = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { Toast.makeText(this, "공고를 열 브라우저가 없어요", Toast.LENGTH_SHORT).show() }
                }, onAddToCalendar = { draft ->
                    openCalendarEditor(draft, startActivity = { startActivity(it) }, onUnavailable = {
                        Toast.makeText(this, "캘린더를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
                    })
                }, onOpenMap = { venue ->
                    openVenueMap(venue, startActivity = { startActivity(it) }, onUnavailable = {
                        Toast.makeText(this, "지도를 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show()
                    })
                })
            }
        }
    }
}
