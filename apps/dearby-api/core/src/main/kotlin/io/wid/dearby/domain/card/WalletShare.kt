package io.wid.dearby.domain.card

import java.time.OffsetDateTime
import java.util.*

// 받은 명함에 연결된 공유 기록
data class WalletShare(val receiptId: UUID, val cardId: UUID, val shareId: UUID, val activities: List<SharedActivity>, val savedAt: OffsetDateTime)
