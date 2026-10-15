package io.wid.dearby.admin.adaptor.web

data class OrganizationCreateRequest(val name: String, val parentOrganizationId: Long? = null)
