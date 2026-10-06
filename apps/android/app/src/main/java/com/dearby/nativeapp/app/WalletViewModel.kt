package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.pages.wallet.WalletEntryState
import com.dearby.nativeapp.pages.wallet.WalletPhase
import com.dearby.nativeapp.pages.wallet.WalletState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 받은 명함 from `GET /v1/wallet` (contract #141). Signed out, there is nothing to show: cards live with the account. */
class WalletViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(WalletState())
    val state = mutable.asStateFlow()

    fun load() = viewModelScope.launch {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.value = WalletState(WalletPhase.SIGNED_OUT); return@launch }
        if (mutable.value.entries.isEmpty()) mutable.update { it.copy(phase = WalletPhase.LOADING) }
        mutable.value = try {
            val wallet = account.authorized { account.client.wallet(it) }
            // Same rule as the web /saved: a card sits under every activity its shares carried, in first-saved order.
            val shares = wallet.shares.sortedBy { it.savedAt }
            val activities = shares.flatMap { it.activities }.distinctBy { it.id }.map { it.id to it.title }
            val byCard = shares.groupBy { it.cardId }.mapValues { (_, list) -> list.flatMap { it.activities }.map { it.id }.distinct() }
            WalletState(WalletPhase.LOADED, wallet.items.map { WalletEntryState(it.card.toState(), byCard[it.card.id].orEmpty()) }, activities)
        } catch (e: AccountException) {
            WalletState(if (e.error == AccountError.UNAUTHORIZED) WalletPhase.SIGNED_OUT else WalletPhase.FAILED)
        }
    }
}
