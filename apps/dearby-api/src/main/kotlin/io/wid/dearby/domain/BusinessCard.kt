package io.wid.dearby.domain

data class BusinessCard(
    val id: Id,
    val userId: UserId,
    val title: String,
    val contacts: List<UserContact>,
    val activityHistory: List<ProfileActivityHistory>
)
