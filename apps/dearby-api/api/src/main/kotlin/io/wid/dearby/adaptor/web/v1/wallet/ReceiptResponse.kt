package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.domain.card.Receipt
import java.time.OffsetDateTime
import java.util.*

// reciprocal은 교환(4단계) 전까지 항상 false
data class ReceiptResponse(val id: UUID, val card: CardResponse, val context: ExchangeContextResponse, val receivedAt: OffsetDateTime, val reciprocal: Boolean) {
    companion object {
        fun from(receipt: Receipt) = ReceiptResponse(
            receipt.id, CardResponse.from(receipt.card, includeTemplate = false),
            ExchangeContextResponse(receipt.context.activityId, receipt.context.label), receipt.receivedAt, false,
        )
    }
}
