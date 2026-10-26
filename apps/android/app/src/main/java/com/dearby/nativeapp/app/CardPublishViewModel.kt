package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PublishPhase {
    data object Editing : PublishPhase
    data object Loading : PublishPhase
    data object Publishing : PublishPhase
    data class Published(val card: PublishedCard) : PublishPhase
    data class Failed(val message: String) : PublishPhase
}
data class CardPublishState(val draft: CardDraft? = null, val phase: PublishPhase = PublishPhase.Editing, val signingIn: Boolean = false, val prefill: PublishedCard? = null)

/** Publishes the composer draft with `POST /v1/cards` (the server copies the profile introduction as the body), or with a [CardPublishState.prefill] saves it onto that same card with `PUT /v1/cards/:id` (contract "명함 고치기는 같은 명함을 수정", 2026-10-20). Profile data is read-only here. */
class CardPublishViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(CardPublishState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN

    /** Opens the composer; signed in, it starts from the saved profile (and [prefill]'s name and selections when editing that card). */
    fun start(prefill: PublishedCard? = null) = launch {
        mutable.value = CardPublishState(prefill = prefill)
        load()
    }
    fun load() = launch {
        if (!signedIn) { mutable.update { it.copy(phase = PublishPhase.Editing, signingIn = true) }; return@launch }
        mutable.update { it.copy(phase = PublishPhase.Loading, signingIn = false) }
        try {
            val stored = account.authorized { account.client.profile(it) }
            mutable.update { it.copy(draft = if (it.draft?.profile == stored) it.draft else CardDraft.from(stored, it.prefill), phase = PublishPhase.Editing) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = if (e.error == AccountError.UNAUTHORIZED) PublishPhase.Editing else PublishPhase.Failed("프로필을 불러오지 못했어요. 다시 시도해 주세요."), signingIn = e.error == AccountError.UNAUTHORIZED) }
        }
    }
    fun edit(draft: CardDraft) = mutable.update { it.copy(draft = draft, phase = if (it.phase is PublishPhase.Failed) PublishPhase.Editing else it.phase) }
    fun cancelSignIn() = mutable.update { it.copy(signingIn = false) }
    fun publish() = launch {
        val draft = mutable.value.draft ?: run { load(); return@launch }
        draft.problem?.let { problem -> mutable.update { it.copy(phase = PublishPhase.Failed(problem)) }; return@launch }
        val editing = mutable.value.prefill
        mutable.update { it.copy(phase = PublishPhase.Publishing) }
        try {
            val card = account.authorized {
                if (editing == null) account.client.publish(draft.cardName, "", draft.contactIds, draft.historyIds, draft.sendsPhoto, it)
                else account.client.updateCard(editing.id, draft.cardName, "", draft.contactIds, draft.historyIds, draft.sendsPhoto, it)
            }
            mutable.update { it.copy(phase = PublishPhase.Published(card)) }
        } catch (e: AccountException) {
            mutable.update { when (e.error) {
                AccountError.UNAUTHORIZED -> it.copy(phase = PublishPhase.Editing, signingIn = true)
                AccountError.BAD_REQUEST -> it.copy(phase = PublishPhase.Failed(e.detail ?: "입력한 내용을 확인해 주세요."))
                AccountError.NOT_FOUND -> it.copy(phase = PublishPhase.Failed("이 명함은 더 이상 없어요. 내 명함 목록을 다시 확인해 주세요."))
                else -> it.copy(phase = PublishPhase.Failed(if (editing == null) "발행하지 못했어요. 잠시 후 다시 시도해 주세요." else "고친 내용을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."))
            } }
        }
    }
    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch { block() }
    }
}
