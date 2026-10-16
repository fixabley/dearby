package io.wid.dearby.application.profile

data class ProfileInput(
    val name: String,
    val job: String,
    val introduction: String,
    val contacts: List<ContactInput>,
    val histories: List<HistoryInput>,
)
