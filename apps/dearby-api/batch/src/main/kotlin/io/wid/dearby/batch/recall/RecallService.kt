package io.wid.dearby.batch.recall

import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.application.guest.GuestRepository
import io.wid.dearby.domain.card.Receipt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 단방향으로 받은 명함 회수(native-v1.md "명함 교환"): 받은 날·저장일 + 30일이 지났고 맞교환이 아니면 recalled_at을 적는다.
// 비로그인 저장은 맞교환이 없어 기간만 본다. 한 트랜잭션의 UPDATE 두 번이라 Spring Batch 잡으로 감싸지 않는다
@Service
class RecallService(private val wallet: WalletRepository, private val guests: GuestRepository) {

    data class Report(val receipts: Int, val guestCards: Int)

    @Transactional
    fun recall(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Report {
        val before = now.minus(Receipt.RECALL_AFTER)
        return Report(wallet.recallOneWay(before, now), guests.recallExpired(before, now))
    }
}
