package io.wid.dearby.application.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.Receipt
import io.wid.dearby.domain.card.WalletShare
import java.time.OffsetDateTime
import java.util.*

// 지갑(받은 명함) 저장소. 구현은 adaptor/persistence. 철회·삭제된 명함은 결과에서 뺀다
interface WalletRepository {

    fun receipts(recipientId: UserId): List<Receipt>

    fun findReceipt(recipientId: UserId, cardId: UUID): Receipt?

    fun createReceipt(id: UUID, recipientId: UserId, cardId: UUID, context: ExchangeContext, receivedAt: OffsetDateTime)

    fun shares(recipientId: UserId): List<WalletShare>

    fun existsShare(receiptId: UUID, shareId: UUID): Boolean

    fun countShares(receiptId: UUID): Int

    fun addShare(receiptId: UUID, cardId: UUID, shareId: UUID, savedAt: OffsetDateTime)
}
