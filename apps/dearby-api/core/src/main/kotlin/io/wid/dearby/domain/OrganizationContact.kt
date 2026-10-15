package io.wid.dearby.domain

data class OrganizationContact(
    val id: Id,
    val organizationId: Id,
    val kind: ContactKind,
    val label: String,
    val value: String,
)
