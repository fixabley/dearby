package io.wid.dearby.admin.adaptor.web

// POST: organizationId|programId|activityId 중 하나와 name 필수. PATCH: name·nameEn·position 중 보낸 값만 바꾸고(귀속은 바꾸지 않는다) nameEn은 빈 문자열이면 지운다
data class RoleRequest(
    val organizationId: Long? = null,
    val programId: Long? = null,
    val activityId: Long? = null,
    val name: String? = null,
    val nameEn: String? = null,
    val position: Int? = null,
)
