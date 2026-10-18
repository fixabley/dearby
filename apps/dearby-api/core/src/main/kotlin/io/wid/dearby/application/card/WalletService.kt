package io.wid.dearby.application.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.Receipt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 받은 명함(지갑). 같은 사용자+명함은 Receipt 하나. 2026-10-18 공유 기능 삭제로 조회만 남았다(새 Receipt를 만드는 경로는 없다)
@Service
class WalletService(private val wallet: WalletRepository) {

    @Transactional(readOnly = true)
    fun list(userId: UserId): List<Receipt> = wallet.receipts(userId)
}
