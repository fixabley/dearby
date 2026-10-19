package io.wid.dearby.adaptor.web.v1.cards

import java.util.*

data class CardPublishRequest(
    val name: String,
    val description: String = "",
    // PUT /v1/cards/{id}에서 비어 있으면 그 명함의 template을 그대로 둔다
    val template: String = "",
    val contactIds: List<UUID> = emptyList(),
    val historyIds: List<UUID> = emptyList(),
)
