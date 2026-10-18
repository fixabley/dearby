package io.wid.dearby.application.card

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.Receipt
import java.time.OffsetDateTime
import java.util.*

// 지갑(받은 명함) 저장소. 구현은 adaptor/persistence. 철회·삭제된 명함과 회수된 행은 결과에서 뺀다
interface WalletRepository {

    fun receipts(recipientId: UserId): List<Receipt>

    fun findReceipt(recipientId: UserId, cardId: UUID): Receipt?

    // 회수됐던 같은 사용자+명함 행이 있으면 되살린다(받은 날·활동을 새로 적는다)
    fun createReceipt(id: UUID, recipientId: UserId, cardId: UUID, activityId: Id?, receivedAt: OffsetDateTime)

    fun setActivity(receiptId: UUID, activityId: Id)

    fun activityExists(activityId: Id): Boolean

    // 단방향으로 받은 명함 중 receivedBefore보다 먼저 받은 것을 회수한다. 회수한 행 수
    fun recallOneWay(receivedBefore: OffsetDateTime, now: OffsetDateTime): Int
}
