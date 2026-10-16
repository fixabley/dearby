package io.wid.dearby.application.card

import java.util.*

// PUT /v1/wallet/shares/:id 결과. created는 명함을 처음 받은 경우(201)
data class WalletSaveResult(val receiptId: UUID, val cardId: UUID, val shareId: UUID, val status: String, val created: Boolean)
