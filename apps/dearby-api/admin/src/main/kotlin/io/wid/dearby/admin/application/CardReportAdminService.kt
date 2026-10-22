package io.wid.dearby.admin.application

import io.wid.dearby.application.card.CardQrCodes
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.card.CardSafetyRepository
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.CardReportListing
import io.wid.dearby.domain.card.ReportStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 명함 신고 처리(admin-v1.md "명함 신고"). 명함 내리기는 주인의 철회와 같은 효과이고 그 명함의 열린 신고를 RESOLVED로 닫는다
@Service
class CardReportAdminService(private val safety: CardSafetyRepository, private val cards: CardRepository, private val codes: CardQrCodes) {

    @Transactional(readOnly = true)
    fun list(status: String?): List<CardReportListing> = safety.reports(status?.let(::status))

    @Transactional
    fun update(id: UUID, status: String?, note: String?, adminId: UserId?): CardReportListing {
        val parsed = status?.let(::status) ?: throw InvalidInputException("상태를 골라 주세요")
        if (!safety.updateReport(id, parsed, note(note), adminId, now())) throw NotFoundException("신고를 찾을 수 없습니다", "card report not found: $id")
        return safety.findReport(id)!!
    }

    // 메모는 필수. 이미 철회된 명함도 성공(신고만 닫는다). reportId가 있으면 그 신고가 이 명함 것인지 본다
    @Transactional
    fun withdraw(cardId: UUID, reportId: UUID?, note: String?, adminId: UserId?) {
        val text = note(note) ?: throw InvalidInputException("내리는 이유를 적어 주세요")
        reportId?.let { safety.findReport(it)?.takeIf { found -> found.report.cardId == cardId } ?: throw NotFoundException("신고를 찾을 수 없습니다", "report $it not for card $cardId") }
        if (cards.revoke(cardId)) codes.revokeAll(cardId)
        safety.resolveOpen(cardId, text, adminId, now())
    }

    private fun status(value: String) = ReportStatus.entries.firstOrNull { it.name == value } ?: throw InvalidInputException("모르는 상태입니다: $value")

    private fun note(value: String?): String? {
        val text = value?.trim()?.ifEmpty { null } ?: return null
        if (text.length > MAX_NOTE) throw InvalidInputException("메모는 ${MAX_NOTE}자까지입니다")
        return text
    }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    private companion object {
        const val MAX_NOTE = 1000
    }
}
