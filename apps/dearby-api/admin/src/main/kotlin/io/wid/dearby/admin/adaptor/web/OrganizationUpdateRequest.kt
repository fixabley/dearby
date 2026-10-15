package io.wid.dearby.admin.adaptor.web

// 보낸 필드만 바꾼다. nameEn은 빈 문자열이면 지운다. 최상위로 옮기려면 clearParent = true
data class OrganizationUpdateRequest(
    val name: String? = null,
    val nameEn: String? = null,
    val parentOrganizationId: Long? = null,
    val clearParent: Boolean = false,
)
