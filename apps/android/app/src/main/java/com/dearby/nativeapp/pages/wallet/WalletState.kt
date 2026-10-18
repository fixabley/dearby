package com.dearby.nativeapp.pages.wallet

import com.dearby.nativeapp.shared.lib.matchesSearch
import com.dearby.nativeapp.shared.lib.nameIndexKey
import com.dearby.nativeapp.shared.lib.nameIndexRank
import com.dearby.nativeapp.widgets.card.cardContent.CardState

enum class WalletPhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/**
 * One received card (contract "명함 교환"): the card face plus where it was exchanged ([program]·[activity]), the day it was
 * received, and its status: [reciprocal] (never recalled) or the recall [badge] (`N일 뒤 사라져요`). [id] is the receipt id.
 */
data class ReceivedCard(val id: String, val card: CardState, val program: String?, val activity: String?, val receivedDate: String,
                        val reciprocal: Boolean, val badge: String?) {
    /** `<프로그램> <활동>에서 받음 · <받은 날>`, or only the day when the exchange had no activity. */
    val origin: String get() = listOfNotNull(listOfNotNull(program, activity).joinToString(" ").ifBlank { null }?.let { "${it}에서 받음" }, receivedDate).joinToString(" · ")
}
/**
 * 받은 명함: the account's wallet (`GET /v1/wallet` receipts). [needSignIn]: a `dearby://cards/…` link arrived while signed out;
 * [opened]: the receipt a link just saved, for the app to open; [notice]: why a link could not be saved. [busy]·[message] belong
 * to 내 명함도 건네기 on the detail page.
 */
data class WalletState(
    val phase: WalletPhase = WalletPhase.LOADING, val cards: List<ReceivedCard> = emptyList(),
    val needSignIn: Boolean = false, val opened: String? = null, val notice: String? = null,
    val busy: Boolean = false, val message: String? = null,
)

/** One index group of the list: the letter (초성 / A–Z / `#`) and its cards in name order. */
data class WalletSection(val key: String, val cards: List<ReceivedCard>)

/** Cards whose name, job, card title, description, body, history titles or exchange program match every search term. */
fun filterWallet(cards: List<ReceivedCard>, query: String): List<ReceivedCard> = cards.filter { received ->
    val card = received.card
    matchesSearch(query, listOfNotNull(card.person, card.job, card.title, card.description, card.introduction, received.program) + card.histories.map { it.title })
}

/** Groups cards by the first letter of the person's name (contract "받은 명함 목록 표시"); empty groups are omitted. */
fun walletSections(cards: List<ReceivedCard>): List<WalletSection> =
    cards.groupBy { nameIndexKey(it.card.person) }.entries
        .sortedBy { nameIndexRank(it.key) }
        .map { (key, members) -> WalletSection(key, members.sortedBy { it.card.person }) }
