package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime
import java.util.*

// 받은 명함(지갑). 같은 사용자+명함은 하나
data class Receipt(val id: UUID, val recipientId: UserId, val card: BusinessCard, val context: ExchangeContext, val receivedAt: OffsetDateTime)
