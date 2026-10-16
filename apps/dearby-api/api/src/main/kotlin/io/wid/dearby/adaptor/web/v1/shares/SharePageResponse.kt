package io.wid.dearby.adaptor.web.v1.shares

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.adaptor.web.v1.cards.CardShareResponse

// 공개 공유 조회: 공유 기록과 그 명함(template 없음)
data class SharePageResponse(val share: CardShareResponse, val card: CardResponse)
