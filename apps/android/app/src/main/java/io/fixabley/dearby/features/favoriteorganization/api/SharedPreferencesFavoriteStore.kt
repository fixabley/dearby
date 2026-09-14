package io.fixabley.dearby.features.favoriteorganization.api

import android.content.SharedPreferences

/** Keeps the existing file, string-set key and asynchronous disk write format. */
internal class SharedPreferencesFavoriteStore(private val preferences: SharedPreferences) : FavoriteStore {
    override fun read(): Set<String> = preferences.getStringSet(IDS_KEY, emptySet())!!.toSet()

    override fun write(ids: Set<String>) {
        preferences.edit().putStringSet(IDS_KEY, ids.toSet()).apply()
    }

    companion object {
        const val FILE_NAME = "dearby.favorites.v1"
        const val IDS_KEY = "organizationIDs"
    }
}
