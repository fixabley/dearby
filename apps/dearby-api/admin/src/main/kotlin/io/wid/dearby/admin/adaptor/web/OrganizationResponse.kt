package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.OrganizationListing

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val parentOrganizationId: Long?,
    val childCount: Int,
    val programCount: Int,
    val ancestors: List<ReferenceResponse>, // 최상위부터 바로 위 조직까지. 최상위 조직은 []
) {
    companion object {
        fun from(listing: OrganizationListing) = with(listing) {
            OrganizationResponse(
                organization.id, organization.name, organization.parentOrganizationId, childCount, programCount,
                ancestors.map { (id, name) -> ReferenceResponse(id, name) },
            )
        }
    }
}
