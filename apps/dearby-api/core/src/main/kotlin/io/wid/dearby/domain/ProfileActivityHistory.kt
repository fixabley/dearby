package io.wid.dearby.domain

data class ProfileActivityHistory(
    val id: Id,
    val userId: UserId,
    val activityId: Id,
    val period: Period,
    val roles: List<Id>,
    val description: String,
    val timestamps: Timestamps
)
