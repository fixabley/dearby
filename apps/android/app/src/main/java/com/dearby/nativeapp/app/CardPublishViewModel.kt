package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountProfile
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
data class CardPublishState(val draft: CardDraft = CardDraft(), val phase: PublishPhase = PublishPhase.Editing, val signingIn: Boolean = false)

/**
 * Publishes the composer draft: `PUT /v1/profile`, then `POST /v1/cards`. When the profile saved but
 * the card did not, a retry publishes only the card. Sign-in is asked for only on publish.
 */
class CardPublishViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(CardPublishState())
    val state = mutable.asStateFlow()
    private var savedProfile: AccountProfile? = null
    private var job: Job? = null
    private val signedIn get() = account.state.value.phase == AccountPhase.SIGNED_IN

    /** Opens a fresh composer; signed in, it starts from the saved profile. */
    fun start() = launch {
        mutable.value = CardPublishState()
        savedProfile = null
        if (!signedIn) return@launch
        mutable.update { it.copy(phase = PublishPhase.Loading) }
        try {
            val stored = account.authorized { account.client.profile(it) }
            savedProfile = stored
            mutable.update { it.copy(draft = CardDraft.from(stored), phase = PublishPhase.Editing) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = PublishPhase.Failed("프로필을 불러오지 못했어요. 다시 시도해 주세요.")) }
        }
    }
    fun edit(draft: CardDraft) = mutable.update { it.copy(draft = draft) }
    fun cancelSignIn() = mutable.update { it.copy(signingIn = false) }
    fun publish() {
        if (!signedIn) { mutable.update { it.copy(signingIn = true) }; return }
        launch { send() }
    }
    /** Signed in from the composer: merge what was typed onto the account, then publish. */
    fun continueAfterSignIn() = launch {
        mutable.update { it.copy(signingIn = false, phase = PublishPhase.Publishing) }
        try {
            val stored = account.authorized { account.client.profile(it) }
            savedProfile = stored
            mutable.update { it.copy(draft = it.draft.merged(stored)) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = PublishPhase.Failed("프로필을 불러오지 못했어요. 다시 시도해 주세요.")) }
            return@launch
        }
        send()
    }
    private suspend fun send() {
        val draft = mutable.value.draft
        if (!draft.canPublish) return
        mutable.update { it.copy(phase = PublishPhase.Publishing) }
        val profile = draft.profile
        try {
            if (savedProfile != profile) {
                account.authorized { account.client.saveProfile(profile, it) }
                savedProfile = profile
            }
            val card = account.authorized { account.client.publish("내 명함", "", draft.contactIds, draft.historyIds, it) }
            mutable.update { it.copy(phase = PublishPhase.Published(card)) }
        } catch (e: AccountException) {
            mutable.update { when (e.error) {
                // The session expired: sign in again and the draft is kept.
                AccountError.UNAUTHORIZED -> it.copy(phase = PublishPhase.Editing, signingIn = true)
                AccountError.INVALID_INPUT -> it.copy(phase = PublishPhase.Failed("입력한 내용을 확인해 주세요. 링크는 https 주소만 쓸 수 있어요."))
                else -> it.copy(phase = PublishPhase.Failed(if (savedProfile == profile) "프로필은 저장했어요. 명함 발행만 다시 시도해 주세요." else "발행하지 못했어요. 잠시 후 다시 시도해 주세요."))
            } }
        }
    }
    private fun launch(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch { block() }
    }
}
