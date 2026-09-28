package com.dearby.nativeapp.entities.card.model

import kotlinx.serialization.Serializable

@Serializable data class CardContactModel(val id: String, val kind: String, val label: String, val value: String)
@Serializable data class CardHistoryModel(val id: String, val title: String, val role: String, val startDate: String, val endDate: String? = null, val description: String = "")
@Serializable data class CardModel(val id: String, val ownerId: String, val name: String, val description: String, val profileName: String, val job: String, val introduction: String, val contacts: List<CardContactModel>, val histories: List<CardHistoryModel>, val createdAt: String)
@Serializable data class ExchangeContextModel(val activityId: String? = null, val label: String? = null) {
    init { require(activityId == null || label == null) { "활동 ID와 직접 입력은 함께 사용할 수 없습니다." } }
}
@Serializable data class ReceiptModel(val id: String, val card: CardModel, val context: ExchangeContextModel, val receivedAt: String, val reciprocal: Boolean)
@Serializable data class GuestSavedCardModel(val cardId: String, val context: ExchangeContextModel, val savedAt: String)
@Serializable data class CardSelectionModel(val name: String, val description: String, val contactIds: Set<String>, val historyIds: Set<String>) {
    fun validated(contactIdsAvailable: Set<String>, historyIdsAvailable: Set<String>): CardSelectionModel {
        require(name.isNotBlank()) { "명함 이름을 입력해 주세요." }
        require(contactIdsAvailable.containsAll(contactIds))
        require(historyIdsAvailable.containsAll(historyIds))
        return this
    }
}
@Serializable data class ImportResultModel(val cardId: String, val status: String, val receiptId: String? = null)
/** Unknown, missing, failed, or conflicting responses never delete a local ID. */
fun importedIds(selected: Set<String>, results: List<ImportResultModel>): Set<String> =
    results.groupBy { it.cardId }.filter { (id, outcomes) -> id in selected && outcomes.size == 1 && outcomes.single().status in setOf("imported", "alreadySaved") && !outcomes.single().receiptId.isNullOrBlank() }.keys
