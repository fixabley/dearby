package io.wid.dearby.application.card

import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.Receipt
import io.wid.dearby.domain.card.WalletShare
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 받은 명함(지갑). 같은 사용자+명함은 Receipt 하나이고 공유 기록은 명함당 20개까지 연결한다
@Service
class WalletService(private val wallet: WalletRepository, private val shares: ShareService) {

    @Transactional(readOnly = true)
    fun list(userId: UserId): Pair<List<Receipt>, List<WalletShare>> = wallet.receipts(userId) to wallet.shares(userId)

    @Transactional
    fun save(userId: UserId, shareId: UUID): WalletSaveResult {
        val (share, card) = shares.get(shareId)
        if (card.ownerId == userId) throw InvalidInputException("내 명함은 저장할 수 없습니다")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val existing = wallet.findReceipt(userId, card.id)
        val receiptId = existing?.id ?: UUID.randomUUID().also { wallet.createReceipt(it, userId, card.id, ExchangeContext(null, null), now) }
        if (wallet.existsShare(receiptId, share.id)) return WalletSaveResult(receiptId, card.id, share.id, "alreadySaved", false)
        if (wallet.countShares(receiptId) >= MAX_SHARES_PER_CARD) throw ConflictException("이 명함의 공유 기록이 ${MAX_SHARES_PER_CARD}개를 넘었습니다")
        wallet.addShare(receiptId, card.id, share.id, now)
        return WalletSaveResult(receiptId, card.id, share.id, "saved", existing == null)
    }

    private companion object {
        const val MAX_SHARES_PER_CARD = 20
    }
}
