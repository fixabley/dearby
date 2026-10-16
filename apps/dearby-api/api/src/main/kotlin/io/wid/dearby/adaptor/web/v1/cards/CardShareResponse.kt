package io.wid.dearby.adaptor.web.v1.cards

import io.wid.dearby.domain.card.CardShare
import java.time.OffsetDateTime
import java.util.*

data class CardShareResponse(val id: UUID, val cardId: UUID, val activities: List<SharedActivityResponse>, val createdAt: OffsetDateTime) {
    companion object {
        fun from(share: CardShare) = CardShareResponse(share.id, share.cardId, share.activities.map { SharedActivityResponse(it.id, it.title) }, share.createdAt)
    }
}
