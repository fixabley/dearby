package io.wid.dearby.application.guest

import io.wid.dearby.domain.card.BusinessCard
import io.wid.dearby.domain.card.ExchangeContext
import java.time.OffsetDateTime

// GET /v1/guest/cards: 저장한 명함(철회·회수 제외). 비로그인 저장은 맞교환이 없어 expiresAt이 항상 있다(savedAt + 30일)
data class GuestWallet(val items: List<GuestItem>)

data class GuestItem(val card: BusinessCard, val context: ExchangeContext, val savedAt: OffsetDateTime, val expiresAt: OffsetDateTime)
