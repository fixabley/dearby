package io.wid.dearby.adaptor.web.v1.cards

import java.util.*

data class CardPublishRequest(
    val name: String,
    val description: String = "",
    val template: String = "",
    // 선택 웹 버전(native-v1.md "웹 버전"). 없거나 비어 있으면 웹 버전 없는 명함
    val webTemplate: String? = null,
    val contactIds: List<UUID> = emptyList(),
    val historyIds: List<UUID> = emptyList(),
)
