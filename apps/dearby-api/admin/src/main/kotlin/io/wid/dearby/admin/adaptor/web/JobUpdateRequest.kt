package io.wid.dearby.admin.adaptor.web

// 보낸 필드만 바꾼다. 최상위로 옮기려면 clearParent
data class JobUpdateRequest(val name: String? = null, val nameEn: String? = null, val parentId: Long? = null, val clearParent: Boolean = false)
