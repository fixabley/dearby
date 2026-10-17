package io.wid.dearby.adaptor.web.v1.guest

import io.wid.dearby.adaptor.web.v1.cards.CardResponse

data class GuestWalletResponse(val items: List<CardResponse>, val shares: List<GuestShareResponse>)
