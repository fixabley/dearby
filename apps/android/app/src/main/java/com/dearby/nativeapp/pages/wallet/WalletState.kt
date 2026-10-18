package com.dearby.nativeapp.pages.wallet

import com.dearby.nativeapp.shared.lib.matchesSearch
import com.dearby.nativeapp.shared.lib.nameIndexKey
import com.dearby.nativeapp.shared.lib.nameIndexRank
import com.dearby.nativeapp.widgets.card.cardContent.CardState

enum class WalletPhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/** 받은 명함: the account's wallet (`GET /v1/wallet` receipts) as display cards. */
data class WalletState(val phase: WalletPhase = WalletPhase.LOADING, val cards: List<CardState> = emptyList())

/** One index group of the list: the letter (초성 / A–Z / `#`) and its cards in name order. */
data class WalletSection(val key: String, val cards: List<CardState>)

/** Cards whose name, job, card title, description, body or history titles match every search term. */
fun filterWallet(cards: List<CardState>, query: String): List<CardState> = cards.filter { card ->
    matchesSearch(query, listOf(card.person, card.job, card.title, card.description, card.introduction) + card.histories.map { it.title })
}

/** Groups cards by the first letter of the person's name (contract "받은 명함 목록 표시"); empty groups are omitted. */
fun walletSections(cards: List<CardState>): List<WalletSection> =
    cards.groupBy { nameIndexKey(it.person) }.entries
        .sortedBy { nameIndexRank(it.key) }
        .map { (key, members) -> WalletSection(key, members.sortedBy { it.person }) }
