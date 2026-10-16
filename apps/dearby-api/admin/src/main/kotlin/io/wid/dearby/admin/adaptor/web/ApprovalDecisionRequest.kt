package io.wid.dearby.admin.adaptor.web

// decision: approve | reject. 승인 때 id는 직접 입력 값을 대신한다. 거절은 reason 필수
data class ApprovalDecisionRequest(
    val decision: String,
    val organizationId: Long? = null,
    val programId: Long? = null,
    val activityId: Long? = null,
    val roleId: Long? = null,
    val reason: String? = null,
)
