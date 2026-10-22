package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.domain.card.CardBlock
import java.time.OffsetDateTime
import java.util.*

// 차단 기록. name은 차단할 때 그 명함의 이름(profileName) 스냅샷
data class BlockResponse(val id: UUID, val name: String, val blockedAt: OffsetDateTime) {
    companion object {
        fun from(it: CardBlock) = BlockResponse(it.id, it.name, it.blockedAt)
    }
}

data class BlockListResponse(val items: List<BlockResponse>)
