package io.wid.dearby.admin.adaptor.web

// 보낸 필드만 바꾼다
data class ProgramUpdateRequest(val name: String? = null, val organizationId: Long? = null)
