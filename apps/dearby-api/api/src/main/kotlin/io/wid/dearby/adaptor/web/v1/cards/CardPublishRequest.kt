package io.wid.dearby.adaptor.web.v1.cards

import java.util.*

data class CardPublishRequest(
    val name: String,
    val description: String = "",
    // 2026-10-26 템플릿 폐지(native-v1.md "템플릿"): 이미 배포된 옛 앱이 보내므로 받기만 하고 쓰지 않는다
    @Suppress("unused") val template: String? = null,
    val contactIds: List<UUID> = emptyList(),
    val historyIds: List<UUID> = emptyList(),
    // 사진 넣기(native-v1.md "프로필 사진"). 생략하면 넣는다. 프로필 사진이 없으면 사진 없는 명함
    val includePhoto: Boolean = true,
    // 소개 링크 넣기(native-v1.md "소개 링크·소개 글 폐지"). 생략하면 넣는다. 프로필에 소개 링크가 없으면 없는 명함
    val includeIntroLink: Boolean = true,
)
