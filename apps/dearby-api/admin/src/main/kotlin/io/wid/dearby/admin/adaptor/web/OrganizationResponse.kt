package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.OrganizationListing

data class OrganizationResponse(
    val id: Long,
    val name: String,
    val parentOrganizationId: Long?,
    val childCount: Int,
    val programCount: Int,
) {
    companion object {
        fun from(listing: OrganizationListing) = with(listing) {
            OrganizationResponse(organization.id, organization.name, organization.parentOrganizationId, childCount, programCount)
        }
    }
}
