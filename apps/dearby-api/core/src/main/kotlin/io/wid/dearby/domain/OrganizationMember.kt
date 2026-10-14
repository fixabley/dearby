package io.wid.dearby.domain

data class OrganizationMember(
    val id: Id,
    val userId: UserId,
    val organizationId: Id,
    val roles: List<Id>,
    val description: String,
    val period: Period,
    val timestamps: Timestamps
)
