package io.wid.dearby.adaptor.web.v1.cards

import java.util.*

data class CardPublishRequest(
    val name: String,
    val description: String = "",
    val template: String = "",
    val contactIds: List<UUID> = emptyList(),
    val historyIds: List<UUID> = emptyList(),
)
