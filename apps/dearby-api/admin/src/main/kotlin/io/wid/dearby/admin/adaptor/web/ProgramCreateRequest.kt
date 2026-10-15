package io.wid.dearby.admin.adaptor.web

data class ProgramCreateRequest(
    val organizationId: Long,
    val name: String,
    val nameEn: String? = null,
    val collection: CollectionSettingsRequest? = null,
)
