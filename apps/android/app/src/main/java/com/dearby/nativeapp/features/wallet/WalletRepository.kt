package com.dearby.nativeapp.features.wallet

import com.dearby.nativeapp.shared.api.*
import com.dearby.nativeapp.shared.storage.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import com.dearby.nativeapp.entities.card.model.*
@Serializable private data class Items<T>(val items: List<T>)
@Serializable data class SendRequest(val cardId: String, val recipientProfileId: String, val context: ExchangeContextModel, val requestId: String)
@Serializable data class DeliveryModel(val receiptId: String, val deliveredAt: String)


class WalletRepository(private val http: HttpClient, private val dao: DearbyDao) {
    suspend fun wallet(): List<ReceiptModel> = wireJson.decodeFromString<Items<ReceiptModel>>(http.request("GET", "/wallet")).items
    suspend fun import(items: List<GuestSavedCardModel>): List<ImportResultModel> = wireJson.decodeFromString<Items<ImportResultModel>>(http.request("POST", "/wallet/import", wireJson.encodeToString(Items(items)))).items
    suspend fun send(request: SendRequest): DeliveryModel = wireJson.decodeFromString<DeliveryModel>(http.request("POST", "/exchanges", wireJson.encodeToString(request))).also { require(it.receiptId.isNotBlank() && it.deliveredAt.isNotBlank()) { "전달 결과를 확인할 수 없습니다. 같은 요청으로 재시도해 주세요." } }
}
