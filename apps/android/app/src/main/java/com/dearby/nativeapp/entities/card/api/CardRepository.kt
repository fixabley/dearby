package com.dearby.nativeapp.entities.card.api

import com.dearby.nativeapp.shared.api.*
import com.dearby.nativeapp.shared.storage.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import com.dearby.nativeapp.entities.card.model.*
@Serializable private data class Items<T>(val items: List<T>)

class CardRepository(private val http: HttpClient, private val dao: DearbyDao) {
    suspend fun cards(): List<CardModel> = wireJson.decodeFromString<Items<CardModel>>(http.request("GET", "/cards")).items
    suspend fun publish(selection: CardSelectionModel): CardModel = wireJson.decodeFromString(http.request("POST", "/cards", wireJson.encodeToString(selection)))
    suspend fun card(id: String): CardModel {
        UUID.fromString(id)
        return wireJson.decodeFromString<CardModel>(http.request("GET", "/cards/$id", authenticated = false)).also { dao.put(DocumentRecord("card:$id", wireJson.encodeToString(it))) }
    }
    suspend fun cachedCard(id: String): CardModel? = dao.document("card:$id")?.let { wireJson.decodeFromString(it.json) }
}
