package io.wid.dearby.batch.recall

import io.wid.dearby.application.card.CardSafetyRepository
import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.application.guest.GuestRepository
import io.wid.dearby.domain.card.CardBlock
import io.wid.dearby.domain.card.CardReport
import io.wid.dearby.domain.card.Receipt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 단방향으로 받은 명함 회수(native-v1.md "명함 교환"): 받은 날·저장일 + 30일이 지났고 맞교환이 아니면 recalled_at을 적는다.
// 비로그인 저장은 맞교환이 없어 기간만 본다. 같은 때 보유 기간이 지난 명함 신고(1개월)와 비로그인 차단(1년, 세션이 지워졌으면 바로)도 지운다(native-v1.md "명함 신고·차단").
// 한 트랜잭션의 UPDATE 두 번과 DELETE 두 번이라 Spring Batch 잡으로 감싸지 않는다
@Service
class RecallService(private val wallet: WalletRepository, private val guests: GuestRepository, private val safety: CardSafetyRepository) {

    data class Report(val receipts: Int, val guestCards: Int, val reports: Int, val guestBlocks: Int)

    @Transactional
    fun recall(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Report {
        val before = now.minus(Receipt.RECALL_AFTER)
        return Report(
            wallet.recallOneWay(before, now),
            guests.recallExpired(before, now),
            safety.deleteReportsBefore(now.minus(CardReport.KEEP_FOR)),
            safety.deleteGuestBlocksBefore(now.minus(CardBlock.GUEST_KEEP_FOR)),
        )
    }
}
