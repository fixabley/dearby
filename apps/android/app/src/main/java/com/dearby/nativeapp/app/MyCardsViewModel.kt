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

/** 내 명함 tab: the signed-in account's published cards (`GET /v1/cards`), newest first, with one selected for the preview. */
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
            cards = try { account.authorized { account.client.cards(it) }.reversed() } catch (e: AccountException) {
                mutable.value = MyCardsState(if (e.error == AccountError.UNAUTHORIZED) MyCardsPhase.SIGNED_OUT else MyCardsPhase.FAILED)
                return@launch
            }
            // 다시 불러와도 고른 명함은 유지하고, 없어졌거나 처음이면 가장 새 명함을 고른다.
            show(cards.find { it.id == mutable.value.selectedId } ?: cards.firstOrNull())
        }
    }
    /** 이 명함 지우기 (`DELETE /v1/cards/:id`): the public link stops working. On failure the card stays and the page shows a message. */
    fun delete(id: String) {
        viewModelScope.launch {
            try { account.authorized { account.client.deleteCard(id, it) } } catch (e: AccountException) {
                mutable.update { it.copy(message = "명함을 지우지 못했어요. 연결을 확인하고 다시 시도해 주세요.") }
                return@launch
            }
            cards = cards.filterNot { it.id == id }
            show(cards.find { it.id == mutable.value.selectedId } ?: cards.firstOrNull())
        }
    }
    private fun show(selected: PublishedCard?) {
        mutable.value = if (cards.isEmpty()) MyCardsState(MyCardsPhase.NO_CARD)
            else MyCardsState(MyCardsPhase.LOADED, cards.map { MyCardChoice(it.id, it.name, it.profileName, it.job) }, selected?.id, selected?.toState())
    }
    /** A grid tile was tapped: show that card under the grid. */
    fun select(id: String) {
        val card = cards.find { it.id == id } ?: return
        mutable.update { it.copy(selectedId = card.id, selected = card.toState()) }
    }
    /** The published card behind a tile, for "이 명함 고치기". */
    fun card(id: String) = cards.find { it.id == id }
}
