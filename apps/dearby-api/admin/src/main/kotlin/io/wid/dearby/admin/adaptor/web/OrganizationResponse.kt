package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.Organization

data class OrganizationResponse(val id: Long, val name: String, val parentOrganizationId: Long?) {
    companion object {
        fun from(organization: Organization) = OrganizationResponse(organization.id, organization.name, organization.parentOrganizationId)
    }
}
