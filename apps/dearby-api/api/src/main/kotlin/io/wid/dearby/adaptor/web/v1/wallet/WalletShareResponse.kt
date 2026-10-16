package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.adaptor.web.v1.cards.SharedActivityResponse
import io.wid.dearby.domain.card.WalletShare
import java.time.OffsetDateTime
import java.util.*

data class WalletShareResponse(val receiptId: UUID, val cardId: UUID, val shareId: UUID, val activities: List<SharedActivityResponse>, val savedAt: OffsetDateTime) {
    companion object {
        fun from(share: WalletShare) = WalletShareResponse(share.receiptId, share.cardId, share.shareId, share.activities.map { SharedActivityResponse(it.id, it.title) }, share.savedAt)
    }
}
