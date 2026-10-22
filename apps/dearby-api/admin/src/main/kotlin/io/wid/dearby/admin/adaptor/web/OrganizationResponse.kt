package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.OrganizationListing

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val parentOrganizationId: Long?,
    val childCount: Int,
    val programCount: Int,
    val ancestors: List<ReferenceResponse>, // 최상위부터 바로 위 조직까지. 최상위 조직은 []
    val logoUrl: String?, // 조직 로고 공개 경로(admin-v1.md "조직 로고"), 없으면 null
) {
    companion object {
        fun from(listing: OrganizationListing) = with(listing) {
            OrganizationResponse(
                organization.id, organization.name, organization.nameEn, organization.parentOrganizationId, childCount, programCount,
                ancestors.map { (id, name) -> ReferenceResponse(id, name) }, logoUrl,
            )
        }
    }
}
