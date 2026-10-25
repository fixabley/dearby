package com.dearby.nativeapp.app

import android.content.Context
import androidx.core.content.edit
import com.dearby.nativeapp.pages.activity.FavoritesView

/**
 * What this device remembers for 즐겨찾기 and push (contract "즐겨찾기(하트)·새 모집 푸시 알림"), in a new preferences file of its own: the last
 * `목록 | 캘린더` choice and whether push started (the first program heart: the notification permission is asked then, once, and FCM may make a token).
 */
class DevicePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("dearby.favorites", Context.MODE_PRIVATE)
    var favoritesView: FavoritesView
        get() = FavoritesView.entries.firstOrNull { it.name == preferences.getString("view", null) } ?: FavoritesView.LIST
        set(value) = preferences.edit { putString("view", value.name) }
    var pushStarted: Boolean
        get() = preferences.getBoolean("pushStarted", false)
        set(value) = preferences.edit { putBoolean("pushStarted", value) }
}
