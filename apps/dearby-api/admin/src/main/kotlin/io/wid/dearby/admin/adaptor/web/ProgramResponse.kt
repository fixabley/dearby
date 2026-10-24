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
    val tags: List<TagResponse> = emptyList(), // 프로그램 태그(native-v1.md "태그")
    val appliedTagActivityCount: Int? = null, // PATCH에서 applyTagsToActivities로 태그가 더해진 활동 수. 다른 응답에서는 null
    val logoUrl: String? = null, // 프로그램 자체 로고 경로(admin-v1.md "프로그램 로고"), 없으면 null
    val cover: CoverResponse? = null, // 대기열 커버(admin-v1.md "프로그램 커버"), 없으면 null
    val effectiveLogo: EffectiveLogoResponse? = null, // 자체 또는 계승한 로고(admin-v1.md "조직 로고"), 없으면 null
) {
    companion object {
        fun from(listing: ProgramListing, appliedActivityCount: Int? = null, appliedTagActivityCount: Int? = null) = ProgramResponse(
            id = listing.program.id,
            name = listing.program.name,
            nameEn = listing.program.nameEn,
            organization = listing.organization?.let { ReferenceResponse(it.id, it.name) },
            category = listing.program.category?.name,
            collection = CollectionSettingsResponse.from(listing),
            appliedActivityCount = appliedActivityCount,
            tags = listing.program.tags.map(TagResponse::from),
            appliedTagActivityCount = appliedTagActivityCount,
            logoUrl = listing.logoUrl,
            cover = listing.cover?.let(CoverResponse::from),
            effectiveLogo = listing.effectiveLogo?.let { EffectiveLogoResponse(it.url, it.source.name, it.organizationId, it.organizationName) },
        )
    }
}

// source: PROGRAM(자체) 또는 ORGANIZATION(조직·상위 조직에서 계승). 계승이면 그 조직 id·이름
data class EffectiveLogoResponse(val url: String, val source: String, val organizationId: Long?, val organizationName: String?)
