package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.QrCode
import com.dearby.nativeapp.pages.cards.CardQrState
import com.dearby.nativeapp.pages.cards.MyCardChoice
import com.dearby.nativeapp.pages.cards.MyCardsPhase
import com.dearby.nativeapp.pages.cards.MyCardsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

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

    // QR 보이기 (contract "명함 QR 코드"): codes live only in memory; the server ends each one 5 minutes after issue at the latest.
    private val qrMutable = MutableStateFlow(CardQrState())
    val qr = qrMutable.asStateFlow()
    /** Issued while the 내 명함 section shows [Pair.first], so the QR screen opens without waiting on a weak connection. */
    private var prefetched: Pair<String, QrCode>? = null
    private var shown: Pair<String, QrCode>? = null
    private var qrJob: Job? = null

    /** The 내 명함 section is visible with [cardId] selected: keep a fresh code for it. An unused one is left to lapse, not revoked. */
    fun prefetch(cardId: String) {
        if (prefetched?.takeIf { it.first == cardId }?.second?.fresh() == true) return
        viewModelScope.launch { runCatching { account.authorized { account.client.issueQrCode(cardId, it) } }.onSuccess { prefetched = cardId to it } }
    }
    /** The QR screen opened for [cardId]: reuse the prefetched code when it is that card's and has over 1 minute left. */
    fun openQr(cardId: String) {
        val ready = prefetched?.takeIf { it.first == cardId && it.second.fresh() }?.second
        prefetched = null
        if (ready != null) showQr(cardId, ready) else issueQr(cardId)
    }
    /** 다시 시도 after a failed issue, or the refresh button (the old code is revoked once the new one is shown). */
    fun refreshQr(cardId: String) = issueQr(cardId)
    /** The QR screen closed: the server keeps the code 2 more minutes for late scans. Failure never blocks closing. */
    fun closeQr() {
        qrJob?.cancel()
        shown?.let(::revoke)
        shown = null
        qrMutable.value = CardQrState()
    }
    private fun issueQr(cardId: String) {
        qrJob?.cancel()
        // A refresh keeps the current QR on screen until the new one arrives, so a scan in progress is not interrupted.
        qrMutable.update { it.copy(failed = false) }
        qrJob = viewModelScope.launch {
            val code = try { account.authorized { account.client.issueQrCode(cardId, it) } } catch (e: AccountException) {
                if (shown == null) qrMutable.value = CardQrState(failed = true)
                return@launch
            }
            shown?.let(::revoke)
            showQr(cardId, code)
        }
    }
    private fun showQr(cardId: String, code: QrCode) {
        shown = cardId to code
        qrMutable.value = CardQrState(code.code)
        qrJob?.cancel()
        // Refresh by itself when 1 minute remains while the screen stays open.
        qrJob = viewModelScope.launch { delay(code.remaining().minus(REFRESH_MARGIN).toMillis().coerceAtLeast(0)); issueQr(cardId) }
    }
    private fun revoke(code: Pair<String, QrCode>) {
        viewModelScope.launch { runCatching { account.authorized { account.client.revokeQrCode(code.first, code.second.id, it) } } }
    }
}

private val REFRESH_MARGIN: Duration = Duration.ofMinutes(1)
private fun QrCode.remaining(): Duration = runCatching { Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(expiresAt)) }.getOrDefault(Duration.ZERO)
private fun QrCode.fresh() = remaining() > REFRESH_MARGIN
