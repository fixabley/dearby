package io.wid.dearby.admin.adaptor.web

data class JobCreateRequest(val name: String, val nameEn: String? = null, val parentId: Long? = null)
