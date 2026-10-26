package io.wid.dearby.application.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.CardBlock
import io.wid.dearby.domain.card.CardReport
import io.wid.dearby.domain.card.CardReportListing
import io.wid.dearby.domain.card.ReportStatus
import java.time.OffsetDateTime
import java.util.*

// 명함 신고·차단 저장소(native-v1.md "명함 신고·차단"). 차단한 쪽은 계정(blockerUser) 또는 게스트 세션 digest(blockerGuest) 중 하나
interface CardSafetyRepository {

    fun createReport(report: CardReport)

    // 신고 시점 명함 사진을 신고 쪽에 복사한다(사진 없는 명함이면 아무것도 하지 않는다). 신고 행과 함께 지워진다
    fun copyCardPhoto(reportId: UUID, cardId: UUID)

    fun reportPhoto(reportId: UUID): ByteArray?

    // 같은 신고자(계정 또는 게스트)가 since 뒤에 같은 명함을 신고한 것. 신고자가 없으면(코드로만 본 사람) 찾지 않는다
    fun recentReport(cardId: UUID, reporterUserId: UserId?, reporterGuestDigest: String?, since: OffsetDateTime): CardReport?

    // 열린 것 먼저, 최신순. status가 null이면 전체
    fun reports(status: ReportStatus?): List<CardReportListing>

    fun findReport(id: UUID): CardReportListing?

    fun updateReport(id: UUID, status: ReportStatus, note: String?, by: UserId?, now: OffsetDateTime): Boolean

    // 운영자가 명함을 내릴 때 그 명함의 열린 신고를 RESOLVED로
    fun resolveOpen(cardId: UUID, note: String, by: UserId?, now: OffsetDateTime)

    // 보유 기간이 지난 신고를 상태와 관계없이 지우고 지운 수를 돌려준다(CardReport.KEEP_FOR)
    fun deleteReportsBefore(at: OffsetDateTime): Int

    // 비로그인 차단 중 at보다 먼저 만들었거나 게스트 세션이 지워진 것을 지우고 지운 수를 돌려준다(CardBlock.GUEST_KEEP_FOR)
    fun deleteGuestBlocksBefore(at: OffsetDateTime): Int

    // 이미 차단했으면 그 행을 돌려준다
    fun block(blockerUser: UserId?, blockerGuest: String?, blockedUser: UserId, name: String, now: OffsetDateTime): CardBlock

    fun isBlocked(blockerUser: UserId?, blockerGuest: String?, blockedUser: UserId): Boolean

    fun blocks(blockerUser: UserId): List<CardBlock>

    fun unblock(blockerUser: UserId, id: UUID): Boolean
}
