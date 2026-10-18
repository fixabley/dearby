package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.domain.card.Receipt
import java.time.OffsetDateTime
import java.util.*

// expiresAt은 서버가 계산한 회수 예정 시각(단방향만), 맞교환이면 null. 클라이언트는 다시 계산하지 않는다
data class ReceiptResponse(
    val id: UUID, val card: CardResponse, val context: ExchangeContextResponse, val receivedAt: OffsetDateTime, val reciprocal: Boolean, val expiresAt: OffsetDateTime?,
) {
    companion object {
        fun from(receipt: Receipt) = ReceiptResponse(
            receipt.id, CardResponse.from(receipt.card, includeTemplate = false), ExchangeContextResponse.from(receipt.context),
            receipt.receivedAt, receipt.reciprocal, receipt.expiresAt,
        )
    }
}
