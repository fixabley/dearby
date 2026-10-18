package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.cards.MyCardChoice
import com.dearby.nativeapp.pages.cards.MyCardsPhase
import com.dearby.nativeapp.pages.cards.MyCardsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 내 명함 tab: the signed-in account's published cards (`GET /v1/cards`), newest first. */
class MyCardsViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(MyCardsState())
    val state = mutable.asStateFlow()
    private var cards: List<PublishedCard> = emptyList()
    private var job: Job? = null

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            if (account.state.value.phase != AccountPhase.SIGNED_IN) { cards = emptyList(); mutable.value = MyCardsState(MyCardsPhase.SIGNED_OUT); return@launch }
            mutable.update { it.copy(phase = MyCardsPhase.LOADING) }
            cards = try { account.authorized { account.client.cards(it) } } catch (e: AccountException) {
                mutable.value = MyCardsState(if (e.error == AccountError.UNAUTHORIZED) MyCardsPhase.SIGNED_OUT else MyCardsPhase.FAILED)
                return@launch
            }
            mutable.value = if (cards.isEmpty()) MyCardsState(MyCardsPhase.NO_CARD)
                else MyCardsState(MyCardsPhase.LOADED, cards.reversed().map { MyCardChoice(it.id, it.name, it.profileName) })
        }
    }
    /** The published card behind a row, for "이 명함 고치기". */
    fun card(id: String) = cards.find { it.id == id }
}
