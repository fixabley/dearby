package io.wid.dearby.domain

data class ActivityContact(
    val id: Id,
    val activityId: Id,
    val kind: ContactKind,
    val label: String,
    val value: String,
)