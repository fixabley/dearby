package io.wid.dearby.application.card

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.CardReport
import io.wid.dearby.domain.card.CardSnapshot
import io.wid.dearby.domain.card.ReportReason
import io.wid.dearby.domain.card.ReportStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 명함 신고(native-v1.md "명함 신고·차단"). 볼 권한이 있는 사람만(CardService.view와 같은 근거). 같은 신고자의 24시간 안 재신고는 기존 신고
@Service
class CardReportService(private val cards: CardService, private val safety: CardSafetyRepository) {

    data class Filed(val report: CardReport, val created: Boolean)

    @Transactional
    fun report(cardId: UUID, viewer: UserId?, guestDigest: String?, guestHolds: Boolean, code: String?, reason: String?, detail: String?): Filed {
        val card = cards.view(cardId, viewer, guestHolds, code)
        if (card.ownerId == viewer) throw InvalidInputException("내 명함은 신고할 수 없어요", "report own card: $cardId")
        val parsed = ReportReason.entries.firstOrNull { it.name == reason } ?: throw InvalidInputException("신고 사유를 골라 주세요")
        val text = detail?.trim()?.ifEmpty { null }
        if (text != null && (text.length > MAX_DETAIL || text.any { it.isISOControl() && it != '\n' })) throw InvalidInputException("설명은 ${MAX_DETAIL}자까지입니다")
        if (parsed == ReportReason.OTHER && text == null) throw InvalidInputException("기타 사유는 설명을 적어 주세요")
        val reporterGuest = guestDigest.takeIf { viewer == null }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        safety.recentReport(cardId, viewer, reporterGuest, now.minus(DEDUPE))?.let { return Filed(it, created = false) }
        val report = CardReport(UUID.randomUUID(), cardId, viewer, reporterGuest, parsed, text, CardSnapshot.of(card), ReportStatus.OPEN, now, null)
        safety.createReport(report)
        return Filed(report, created = true)
    }

    private companion object {
        const val MAX_DETAIL = 500
        val DEDUPE: Duration = Duration.ofHours(24)
    }
}
