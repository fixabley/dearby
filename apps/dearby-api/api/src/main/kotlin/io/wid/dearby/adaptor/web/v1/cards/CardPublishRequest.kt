package io.wid.dearby.adaptor.web.v1.cards

import java.util.*

data class CardPublishRequest(
    val name: String,
    val description: String = "",
    // 2026-10-26 템플릿 폐지(native-v1.md "템플릿"): 이미 배포된 옛 앱이 보내므로 받기만 하고 쓰지 않는다
    @Suppress("unused") val template: String? = null,
    val contactIds: List<UUID> = emptyList(),
    val historyIds: List<UUID> = emptyList(),
)
