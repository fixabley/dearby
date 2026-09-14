package io.fixabley.dearby.features.favoriteorganization.api


internal interface FavoriteStore {
    fun read(): Set<String>
    fun write(ids: Set<String>)
}
