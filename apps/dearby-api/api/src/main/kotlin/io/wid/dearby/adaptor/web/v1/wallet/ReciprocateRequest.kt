package io.wid.dearby.adaptor.web.v1.wallet

import java.util.*

// POST /v1/wallet/cards/:cardId/reciprocate: 받은 명함의 주인에게 건넬 내 명함
data class ReciprocateRequest(val myCardId: UUID)
