package io.fixabley.dearby.core.data


interface FavoriteStore {
    fun read(): Set<String>
    fun write(ids: Set<String>)
}
