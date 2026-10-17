package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.ApprovalStatus
import java.util.*

// 관리자 직무 승인(admin 모듈). 구현은 adaptor/persistence
interface ProfileJobReviewRepository {

    // 직접 입력한 직무 태그와 작성자 프로필 이름. id 오름차순 keyset
    fun search(approval: ApprovalStatus?, after: UUID?, size: Int): List<ProfileJobForReview>

    fun find(id: UUID): ProfileJobForReview?

    // 승인 때 jobId를 주면 직접 입력 이름을 그 직무로 바꾼다
    fun updateApproval(id: UUID, status: ApprovalStatus, reason: String?, jobId: Id?): Boolean
}
