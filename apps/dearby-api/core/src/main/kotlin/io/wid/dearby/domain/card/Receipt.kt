package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

// 받은 명함(지갑). 같은 사용자+명함은 하나. reciprocal은 명함 주인도 내 명함 하나를 갖고 있는지(계정 쌍 기준),
// expiresAt은 단방향일 때의 회수 예정 시각(receivedAt + RECALL_AFTER), 맞교환이면 null
data class Receipt(
    val id: UUID, val recipientId: UserId, val card: BusinessCard, val context: ExchangeContext, val receivedAt: OffsetDateTime,
    val reciprocal: Boolean, val expiresAt: OffsetDateTime?,
) {
    companion object {
        // 단방향으로 받은 명함을 회수하기까지(2026-10-19 사용자 결정 "1달")
        val RECALL_AFTER: Duration = Duration.ofDays(30)
    }
}
