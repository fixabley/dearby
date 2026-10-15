package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.ProgramListing

// organization은 논리키라 없을 수 있다(null)
data class ProgramResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val organization: ReferenceResponse?,
    val collection: CollectionSettingsResponse,
) {
    companion object {
        fun from(listing: ProgramListing) = ProgramResponse(
            id = listing.program.id,
            name = listing.program.name,
            nameEn = listing.program.nameEn,
            organization = listing.organization?.let { ReferenceResponse(it.id, it.name) },
            collection = CollectionSettingsResponse.from(listing),
        )
    }
}
