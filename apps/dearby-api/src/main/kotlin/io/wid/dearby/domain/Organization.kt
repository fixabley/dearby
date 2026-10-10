package io.wid.dearby.domain

data class Organization(
    val id: Id,
    val name: String,
    val contacts: List<OrganizationContact>,
    val parentOrganizationId: Id?,
    val timestamps: Timestamps,
    val address: Address,
)