package io.wid.dearby.application.guest

import io.wid.dearby.domain.card.BusinessCard
import io.wid.dearby.domain.card.SharedActivity
import java.time.OffsetDateTime
import java.util.*

// GET /v1/guest/cards: 저장한 명함(철회 제외)과 공유 기록(저장 순)
data class GuestWallet(val items: List<BusinessCard>, val shares: List<GuestShare>)

data class GuestShare(val cardId: UUID, val shareId: UUID, val activities: List<SharedActivity>, val savedAt: OffsetDateTime)
