package io.wid.dearby.domain


data class UserContact(
    val id: Id,
    val userId: UserId,
    val kind: ContactKind,
    val label: String,
    val value: String,
)
