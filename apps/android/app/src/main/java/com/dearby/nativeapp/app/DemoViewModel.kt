package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.pages.profile.ProfileState
import com.dearby.nativeapp.pages.wallet.WalletEntryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DemoState(
    val loggedIn: Boolean = false, val profile: ProfileState = demoProfile,
    val cards: List<CardState> = demoCards,
    val wallet: List<WalletEntryState> = demoWallet, val query: String = "", val reciprocalGroup: Boolean = false,
)
class DemoViewModel : ViewModel() {
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    fun login(value: Boolean) { mutable.update { it.copy(loggedIn = value) } }
    fun profile(value: ProfileState) { mutable.update { it.copy(profile = value) } }
    fun query(value: String) { mutable.update { it.copy(query = value) } }
    fun group(reciprocal: Boolean) { mutable.update { it.copy(reciprocalGroup = reciprocal) } }
    fun saveCard(card: CardState) { mutable.update { if (it.wallet.any { entry -> entry.card.id == card.id }) it else it.copy(wallet = it.wallet + WalletEntryState(card, false)) } }
    fun send(cardId: String, recipient: CardState) {
        require(mutable.value.cards.any { it.id == cardId })
        saveCard(recipient)
        mutable.update { it.copy(wallet = it.wallet.map { entry -> if (entry.card.id == recipient.id) entry.copy(reciprocal = true) else entry }, query = "") }
    }
}
