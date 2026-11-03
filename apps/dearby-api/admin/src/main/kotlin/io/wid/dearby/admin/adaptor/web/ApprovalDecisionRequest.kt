package io.wid.dearby.admin.adaptor.web

// decision: approve | reject. 승인 때 ownerKind·ownerId는 이름만 적은 단위를 등록된 단위로, roleId는 역할 이름을 그 단위의 역할로 바꾼다. 거절은 reason 필수
data class ApprovalDecisionRequest(
    val decision: String,
    val ownerKind: String? = null,
    val ownerId: Long? = null,
    val roleId: Long? = null,
    val reason: String? = null,
)
