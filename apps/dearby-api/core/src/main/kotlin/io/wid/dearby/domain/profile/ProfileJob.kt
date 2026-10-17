package io.wid.dearby.domain.profile

import java.util.*

// 프로필의 직무 태그. 직무 활동 정보에서 고르면 job, 직접 입력이면 jobName만 있다(jobName은 고른 경우에도 그 이름을 담는다).
// 직접 입력은 approval이 PENDING이라 승인 전에는 명함의 직무에 들어가지 않는다
data class ProfileJob(
    val id: UUID,
    val job: ActivityInfoRef?,
    val jobName: String?,
    val approval: ApprovalStatus,
    val approvalReason: String?,
) {
    val isCustom: Boolean get() = job == null

    val title: String get() = job?.name ?: jobName ?: ""

    // 승인 여부 판단에 쓰는 값(바뀌면 다시 PENDING)
    val customEntry: String? get() = job?.id?.toString() ?: jobName
}
