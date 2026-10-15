package io.wid.dearby.admin.adaptor.web

data class ProgramCreateRequest(val organizationId: Long, val name: String, val collection: CollectionSettingsRequest? = null)
