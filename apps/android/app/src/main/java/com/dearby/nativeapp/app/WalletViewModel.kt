package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountHistory
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
    histories.map { CardHistoryState(it.id, it.chain.joinToString(" · "), it.roleTitle, ContactRules.period(it.startDate, it.endDate), it.isVerified, it.description) },
    // 목록 칩용 소속: 승인된 이력 중 진행 중인 것, 그다음 시작일이 늦은 것(contract "받은 명함 목록 표시")
    organization = histories.filter { it.isApproved }.sortedWith(compareBy<AccountHistory> { it.endDate != null }.thenByDescending { it.startDate })
        .firstNotNullOfOrNull { it.organization?.name ?: it.organizationName }?.takeIf { it.isNotBlank() })
