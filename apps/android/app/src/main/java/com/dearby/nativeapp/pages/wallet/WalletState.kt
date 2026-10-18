package com.dearby.nativeapp.pages.wallet

import com.dearby.nativeapp.shared.lib.matchesSearch
import com.dearby.nativeapp.widgets.card.cardContent.CardState

enum class WalletPhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/** 받은 명함: the account's wallet (`GET /v1/wallet` receipts) as display cards. */
data class WalletState(val phase: WalletPhase = WalletPhase.LOADING, val cards: List<CardState> = emptyList())

/** Cards whose name, job, card title, description, body or history titles match every search term. */
fun filterWallet(cards: List<CardState>, query: String): List<CardState> = cards.filter { card ->
    matchesSearch(query, listOf(card.person, card.job, card.title, card.description, card.introduction) + card.histories.map { it.title })
}
