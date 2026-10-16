package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.History
import io.wid.dearby.domain.profile.VerificationStatus
import java.util.*

// 관리자 검토용(admin 모듈). 구현은 adaptor/persistence
interface HistoryReviewRepository {

    // 검토할 이력과 작성자 프로필 이름. id 오름차순 keyset(after는 이전 페이지 마지막 id)
    fun search(approval: ApprovalStatus?, verification: VerificationStatus?, after: UUID?, size: Int): List<HistoryForReview>

    fun find(historyId: UUID): HistoryForReview?

    // 승인 때 직접 입력 값을 카탈로그 id로 바꾼다(null이면 그대로)
    fun updateApproval(historyId: UUID, status: ApprovalStatus, reason: String?, organizationId: Id?, programId: Id?, activityId: Id?, roleId: Id?): Boolean

    fun updateVerification(historyId: UUID, status: VerificationStatus): Boolean
}
