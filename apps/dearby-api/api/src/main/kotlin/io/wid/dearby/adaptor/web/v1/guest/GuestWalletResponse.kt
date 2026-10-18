package io.wid.dearby.adaptor.web.v1.guest

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.adaptor.web.v1.wallet.ExchangeContextResponse
import io.wid.dearby.application.guest.GuestItem
import java.time.OffsetDateTime

// GET /v1/guest/cards (native-v1.md "명함 교환"): 2026-10-19부터 Card[]가 아니라 GuestItem[]. expiresAt은 항상 savedAt + 30일
data class GuestWalletResponse(val items: List<GuestItemResponse>)

data class GuestItemResponse(val card: CardResponse, val context: ExchangeContextResponse, val savedAt: OffsetDateTime, val expiresAt: OffsetDateTime) {
    companion object {
        fun from(item: GuestItem) = GuestItemResponse(CardResponse.from(item.card, includeTemplate = false), ExchangeContextResponse.from(item.context), item.savedAt, item.expiresAt)
    }
}
