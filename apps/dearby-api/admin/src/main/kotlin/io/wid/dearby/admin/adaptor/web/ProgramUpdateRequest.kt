package io.wid.dearby.admin.adaptor.web

// 보낸 필드만 바꾼다. nameEn은 빈 문자열이면 지운다
data class ProgramUpdateRequest(val name: String? = null, val nameEn: String? = null, val organizationId: Long? = null)
