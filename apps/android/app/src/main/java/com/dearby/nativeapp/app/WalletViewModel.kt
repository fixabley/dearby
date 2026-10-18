package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.wallet.WalletPhase
import com.dearby.nativeapp.pages.wallet.WalletState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 받은 명함 from `GET /v1/wallet`. Signed out, there is nothing to show: cards live with the account. */
class WalletViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(WalletState())
    val state = mutable.asStateFlow()

    fun load() = viewModelScope.launch {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.value = WalletState(WalletPhase.SIGNED_OUT); return@launch }
        if (mutable.value.cards.isEmpty()) mutable.update { it.copy(phase = WalletPhase.LOADING) }
        mutable.value = try {
            val wallet = account.authorized { account.client.wallet(it) }
            WalletState(WalletPhase.LOADED, wallet.items.map { it.card.toState() })
        } catch (e: AccountException) {
            WalletState(if (e.error == AccountError.UNAUTHORIZED) WalletPhase.SIGNED_OUT else WalletPhase.FAILED)
        }
    }
}

fun PublishedCard.toState() = CardState(id, profileName, job, name, description, introduction,
    contacts.map { ContactState(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.chain.joinToString(" · "), it.roleTitle, ContactRules.period(it.startDate, it.endDate), it.isVerified) })
