package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.ProgramListing

// organization은 논리키라 없을 수 있다(null)
data class ProgramResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val organization: ReferenceResponse?,
    val category: String?, // 프로그램 기본 종류 코드(admin-v1.md "프로그램 기본 종류")
    val collection: CollectionSettingsResponse,
    val appliedActivityCount: Int? = null, // PATCH에서 applyCategoryToActivities로 바뀐 활동 수. 다른 응답에서는 null
) {
    companion object {
        fun from(listing: ProgramListing, appliedActivityCount: Int? = null) = ProgramResponse(
            id = listing.program.id,
            name = listing.program.name,
            nameEn = listing.program.nameEn,
            organization = listing.organization?.let { ReferenceResponse(it.id, it.name) },
            category = listing.program.category?.name,
            collection = CollectionSettingsResponse.from(listing),
            appliedActivityCount = appliedActivityCount,
        )
    }
}
