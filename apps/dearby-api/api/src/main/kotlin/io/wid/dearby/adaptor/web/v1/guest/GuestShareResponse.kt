package io.wid.dearby.adaptor.web.v1.guest

import io.wid.dearby.adaptor.web.v1.cards.SharedActivityResponse
import io.wid.dearby.application.guest.GuestShare
import java.time.OffsetDateTime
import java.util.*

data class GuestShareResponse(val cardId: UUID, val shareId: UUID, val activities: List<SharedActivityResponse>, val savedAt: OffsetDateTime) {
    companion object {
        fun from(share: GuestShare) = GuestShareResponse(share.cardId, share.shareId, share.activities.map { SharedActivityResponse(it.id, it.title) }, share.savedAt)
    }
}
