package com.dearby.nativeapp.features.guest

import com.dearby.nativeapp.entities.profile.model.*
import com.dearby.nativeapp.entities.card.model.*
import com.dearby.nativeapp.shared.api.wireJson
import com.dearby.nativeapp.shared.storage.*
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID

class GuestStore(private val dao: DearbyDao) {
    suspend fun all(): List<GuestSavedCardModel> = dao.guests().map { GuestSavedCardModel(it.cardId, wireJson.decodeFromString(it.contextJson), it.savedAt) }
    suspend fun save(id: String, context: ExchangeContextModel) {
        UUID.fromString(id)
        dao.saveGuest(GuestRecord(id, wireJson.encodeToString(context), Instant.now().toString()))
    }
    suspend fun applyImport(selected: Set<String>, results: List<ImportResultModel>) { dao.removeGuests(importedIds(selected, results)) }
}
