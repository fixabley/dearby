package io.wid.dearby.application.guest

import io.wid.dearby.domain.card.BusinessCard

// GET /v1/guest/cards: 저장한 명함(철회 제외)
data class GuestWallet(val items: List<BusinessCard>)
