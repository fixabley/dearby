package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime
import java.time.Period
import java.util.*

// 명함 신고(native-v1.md "명함 신고·차단"). 스냅샷은 신고 시점의 명함 내용이라 주인이 고치거나 지워도 근거가 남는다
enum class ReportReason { SPAM, IMPERSONATION, INAPPROPRIATE, OTHER }

enum class ReportStatus { OPEN, RESOLVED, DISMISSED }

// introduction은 2026-10-26 소개 글 폐지 전 신고의 근거로만 남는다(새 신고는 빈 문자열)
data class CardSnapshot(val name: String, val profileName: String, val job: String, val introduction: String = "", val contacts: List<SnapshotContact>, val introLink: String? = null) {
    data class SnapshotContact(val kind: String, val label: String, val value: String)

    companion object {
        fun of(card: BusinessCard) = CardSnapshot(card.name, card.profileName, card.job, contacts = card.contacts.map { SnapshotContact(it.kind.name, it.label, it.value) }, introLink = card.introLink)
    }
}

data class CardReport(
    val id: UUID,
    val cardId: UUID,
    val reporterUserId: UserId?,
    val reporterGuestDigest: String?,
    val reason: ReportReason,
    val detail: String?,
    val snapshot: CardSnapshot,
    val status: ReportStatus,
    val createdAt: OffsetDateTime,
    val note: String?,
) {
    companion object {
        // 보유 기간(2026-10-25 사용자 결정): 신고일(createdAt)로부터 1개월이 지나면 상태와 관계없이 지운다(batch RecallService)
        val KEEP_FOR: Period = Period.ofMonths(1)
    }
}

// 어드민 목록 한 줄: 같은 명함 신고 수와 명함이 이미 내려갔는지(철회·삭제)
data class CardReportListing(val report: CardReport, val reportCount: Int, val cardWithdrawn: Boolean)

// 차단(차단 단위는 명함 주인). name은 차단할 때 그 명함의 이름
data class CardBlock(val id: UUID, val name: String, val blockedAt: OffsetDateTime) {
    companion object {
        // 비로그인 차단 보유 기간(2026-10-25 사용자 결정): 차단일(createdAt)로부터 1년. 세션이 지워진 차단은 기간과 관계없이 지운다(batch RecallService). 로그인 차단은 대상 아님
        val GUEST_KEEP_FOR: Period = Period.ofYears(1)
    }
}
