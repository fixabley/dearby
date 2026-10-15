package io.wid.dearby.admin.adaptor.web

data class OrganizationCreateRequest(val name: String, val nameEn: String? = null, val parentOrganizationId: Long? = null)
