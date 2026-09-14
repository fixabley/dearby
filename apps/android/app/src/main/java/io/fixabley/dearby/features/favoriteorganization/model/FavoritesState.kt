package io.fixabley.dearby.features.favoriteorganization.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore

/** One root-owned state, observed by Compose and changed on the UI thread. */
internal class FavoritesState(private val store: FavoriteStore) {
    var ids: Set<String> by mutableStateOf(store.read().toSet())
        private set

    fun save(id: String) {
        update(ids + id)
    }

    fun remove(id: String) {
        update(ids - id)
    }

    private fun update(next: Set<String>) {
        store.write(next)
        ids = next
    }
}
