package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.qr.QrCardChoice
import com.dearby.nativeapp.pages.qr.QrSharePhase
import com.dearby.nativeapp.pages.qr.QrShareState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The QR tab's real share: the signed-in account's card, shared with optional activities as `<web>/s/<id>`.
 * One share is made per card and activity choice in this app session, so re-entering the tab reuses it.
 */
class QrShareViewModel(private val account: AccountViewModel, private val link: (String) -> String) : ViewModel() {
    private val mutable = MutableStateFlow(QrShareState())
    val state = mutable.asStateFlow()
    private var cards: List<PublishedCard> = emptyList()
    private val links = mutableMapOf<String, String>()
    private var job: Job? = null

    fun load() = launch {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.value = QrShareState(QrSharePhase.SIGNED_OUT); return@launch }
        mutable.update { it.copy(phase = QrSharePhase.LOADING, error = null) }
        cards = try { account.authorized { account.client.cards(it) } } catch (e: AccountException) {
            mutable.update { if (e.error == AccountError.UNAUTHORIZED) QrShareState(QrSharePhase.SIGNED_OUT)
                else it.copy(phase = QrSharePhase.FAILED, error = "명함을 불러오지 못했어요. 다시 시도해 주세요.") }
            return@launch
        }
        val newest = cards.lastOrNull() ?: run { mutable.value = QrShareState(QrSharePhase.NO_CARD); return@launch }
        val selected = mutable.value.selectedCardId?.takeIf { id -> cards.any { it.id == id } } ?: newest.id
        mutable.update { it.copy(cards = cards.reversed().map { card -> QrCardChoice(card.id, card.name, card.profileName) }, selectedCardId = selected) }
        share()
    }
    fun select(cardId: String) = launch { mutable.update { it.copy(selectedCardId = cardId) }; share() }
    fun toggle(activityId: String) = launch {
        mutable.update { it.copy(activityIds = if (activityId in it.activityIds) it.activityIds - activityId else it.activityIds + activityId) }
        share()
    }
    private suspend fun share() {
        val current = mutable.value
        val card = cards.find { it.id == current.selectedCardId } ?: return
        val ids = current.activityIds.sorted()
        val key = (listOf(card.id) + ids).joinToString(" ")
        mutable.update { it.copy(name = card.profileName, job = card.job) }
        links[key]?.let { url -> mutable.update { it.copy(phase = QrSharePhase.READY, url = url, error = null) }; return }
        mutable.update { it.copy(phase = QrSharePhase.LOADING, url = null, error = null) }
        try {
            val url = link(account.authorized { account.client.share(card.id, ids, it) }.id)
            links[key] = url
            mutable.update { it.copy(phase = QrSharePhase.READY, url = url) }
        } catch (e: AccountException) {
            mutable.update { when (e.error) {
                AccountError.UNAUTHORIZED -> QrShareState(QrSharePhase.SIGNED_OUT)
                AccountError.INVALID_INPUT -> it.copy(phase = QrSharePhase.FAILED, error = "고른 활동 중 지금 모집 정보에 없는 활동이 있어요. 활동 선택을 바꿔 주세요.")
                else -> it.copy(phase = QrSharePhase.FAILED, error = "QR을 만들지 못했어요. 다시 시도해 주세요.")
            } }
        }
    }
    // A later card or activity choice cancels the share still in flight, so only the current choice shows.
    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch { block() }
    }
}
