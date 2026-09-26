package com.dearby.nativeapp.features.wallet

import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.api.wireJson
import com.dearby.nativeapp.shared.storage.DearbyDao
import com.dearby.nativeapp.shared.storage.DocumentRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.dearby.nativeapp.entities.card.model.GuestSavedCardModel
import com.dearby.nativeapp.entities.card.model.ImportResultModel
import com.dearby.nativeapp.entities.card.model.ReceiptModel
@Serializable private data class Items<T>(val items: List<T>)
@Serializable data class SendRequest(val cardId: String, val recipientProfileId: String, val context: ExchangeContextModel, val requestId: String)
@Serializable data class DeliveryModel(val receiptId: String, val deliveredAt: String)


class WalletRepository(private val http: HttpClient, private val dao: DearbyDao) {
    suspend fun wallet(): List<ReceiptModel> = wireJson.decodeFromString<Items<ReceiptModel>>(http.request("GET", "/wallet")).items
    suspend fun import(items: List<GuestSavedCardModel>): List<ImportResultModel> = wireJson.decodeFromString<Items<ImportResultModel>>(http.request("POST", "/wallet/import", wireJson.encodeToString(Items(items)))).items
    suspend fun send(cardId: String, recipientProfileId: String, context: ExchangeContextModel, accountId: String): DeliveryModel {
        val key = "account:$accountId:pending-send"
        val previous = dao.document(key)?.let { wireJson.decodeFromString<SendRequest>(it.json) }
        val request = if (previous?.cardId == cardId && previous.recipientProfileId == recipientProfileId && previous.context == context) previous
            else SendRequest(cardId, recipientProfileId, context, UUID.randomUUID().toString())
        // Save before I/O. Ambiguous responses/restarts retain the same idempotency key.
        dao.put(DocumentRecord(key, wireJson.encodeToString(request)))
        val result = wireJson.decodeFromString<DeliveryModel>(http.request("POST", "/exchanges", wireJson.encodeToString(request)))
        require(result.receiptId.isNotBlank() && result.deliveredAt.isNotBlank()) { "전달 결과를 확인할 수 없습니다. 같은 요청으로 재시도해 주세요." }
        dao.removeDocument(key)
        return result
    }
}
