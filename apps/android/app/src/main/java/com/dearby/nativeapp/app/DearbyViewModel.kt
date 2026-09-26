package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.profile.api.ProfileRepository
import com.dearby.nativeapp.entities.card.api.CardRepository
import com.dearby.nativeapp.features.account.AuthRepository
import com.dearby.nativeapp.features.wallet.WalletRepository
import com.dearby.nativeapp.entities.profile.model.ProfileModel
import com.dearby.nativeapp.entities.card.model.CardSelectionModel
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.dearby.nativeapp.entities.card.model.importedIds
import com.dearby.nativeapp.features.account.AccountState
import com.dearby.nativeapp.features.guest.GuestStore
import com.dearby.nativeapp.shared.api.ApiFailure
import com.dearby.nativeapp.shared.storage.TokenVault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DearbyViewModel(private val profiles: ProfileRepository, private val cardsRepository: CardRepository, private val wallet: WalletRepository, private val auth: AuthRepository, private val guests: GuestStore, private val vault: TokenVault) : ViewModel() {
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    init { action {
        val loggedIn = withContext(Dispatchers.IO) { vault.read() != null }
        profiles.accountId = withContext(Dispatchers.IO) { vault.profileId() }
        mutable.update { it.copy(loggedIn = loggedIn, profile = profiles.localProfile(loggedIn), ready = true) }
        reloadGuests()
        if (loggedIn) refreshAccount()
    } }
    private fun action(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.update { it.copy(message = failure.message ?: "처리하지 못했습니다. 다시 시도해 주세요.", ready = true) }
                if (failure is ApiFailure && failure.status == 401 && mutable.value.loggedIn) {
                    withContext(Dispatchers.IO) { vault.clear() }
                    profiles.accountId = null
                    val draft = profiles.localProfile(false)
                    mutable.update { it.copy(loggedIn = false, profile = draft, cards = emptyList(), wallet = emptyList(), selectedCardId = null, importVisible = false, message = "인증이 만료되었습니다. 다시 로그인해 주세요.") }
                }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun report(message: String) { mutable.update { it.copy(message = message) } }
    private suspend fun reloadGuests() {
        val local = guests.all()
        val cache = local.mapNotNull { saved -> cardsRepository.cachedCard(saved.cardId)?.let { saved.cardId to it } }.toMap()
        mutable.update { it.copy(guests = local, guestCards = cache) }
    }
    private suspend fun refreshAccount() {
        val profile = profiles.profile()
        mutable.update { it.copy(profile = profile) }
        val cards = cardsRepository.cards()
        mutable.update { it.copy(cards = cards, selectedCardId = it.selectedCardId?.takeIf { id -> cards.any { card -> card.id == id } } ?: cards.firstOrNull()?.id) }
        val wallet = wallet.wallet()
        mutable.update { it.copy(wallet = wallet) }
    }
    fun refresh() = action { reloadGuests(); if (mutable.value.loggedIn) refreshAccount() }
    fun saveProfile(profile: ProfileModel) = action {
        require(profile.name.isNotBlank()) { "이름을 입력해 주세요." }
        require(profile.contacts.all { it.value.isNotBlank() }) { "비어 있는 연락처를 입력하거나 삭제해 주세요." }
        profile.histories.forEach { history ->
            val start = java.time.LocalDate.parse(history.startDate)
            history.endDate?.let { require(!java.time.LocalDate.parse(it).isBefore(start)) { "종료일은 시작일 이후여야 합니다." } }
            require(history.title.isNotBlank()) { "활동 이름을 입력해 주세요." }
        }
        val saved = if (mutable.value.loggedIn) profiles.saveProfile(profile) else profile.also { profiles.saveDraft(it) }
        mutable.update { it.copy(profile = saved, message = if (it.loggedIn) "프로필을 저장했습니다." else "이 기기에 초안을 저장했습니다.") }
    }
    fun requestCode(email: String) = action {
        require(email.contains('@')) { "이메일 주소를 확인해 주세요." }
        val challenge = auth.challenge(email.trim())
        mutable.update { it.copy(challengeId = challenge.challengeId, challengeExpires = challenge.expiresAt, message = "인증번호를 요청했습니다. 이메일을 확인해 주세요.") }
    }
    fun login(code: String) = action {
        val session = auth.login(requireNotNull(mutable.value.challengeId), code.trim())
        withContext(Dispatchers.IO) { vault.write(session.sessionToken, session.profileId) }
        profiles.clearAccount()
        profiles.accountId = session.profileId
        mutable.update { it.copy(loggedIn = true, profile = ProfileModel(), cards = emptyList(), wallet = emptyList(), challengeId = null, importVisible = it.guests.isNotEmpty()) }
        refreshAccount()
    }
    fun logout() = action {
        auth.logout()
        withContext(Dispatchers.IO) { vault.clear() }
        profiles.clearAccount()
        profiles.accountId = null
        val local = profiles.localProfile(false)
        mutable.update { it.copy(loggedIn = false, profile = local, cards = emptyList(), wallet = emptyList(), selectedCardId = null, importVisible = false) }
    }
    fun showImport(show: Boolean) { mutable.update { it.copy(importVisible = show) } }
    fun importSelected(selected: Set<String>) = action {
        val requested = guests.all().filter { it.cardId in selected }
        if (requested.isEmpty()) return@action
        val results = wallet.import(requested)
        guests.applyImport(requested.map { it.cardId }.toSet(), results)
        reloadGuests()
        val success = importedIds(selected, results).size
        mutable.update { it.copy(message = "${success}개 가져왔습니다. 미선택·실패 항목은 기기에 남아 있습니다.", importVisible = it.guests.isNotEmpty()) }
        val wallet = wallet.wallet()
        mutable.update { it.copy(wallet = wallet) }
    }
    fun receive(id: String, context: ExchangeContextModel) = action {
        // Only validated public cards enter the wallet; existing saved IDs survive later failures.
        cardsRepository.card(id)
        guests.save(id, context)
        reloadGuests()
        mutable.update { it.copy(message = "이 기기에 명함 ID를 저장했습니다.") }
    }
    fun selectCard(id: String?) { mutable.update { it.copy(selectedCardId = id) } }
    fun publish(selection: CardSelectionModel, onSuccess: () -> Unit) = action {
        require(mutable.value.loggedIn) { "명함 게시에는 이메일 로그인이 필요합니다." }
        val card = cardsRepository.publish(selection.validated(mutable.value.profile.contacts.map { it.id }.toSet(), mutable.value.profile.histories.map { it.id }.toSet()))
        mutable.update { it.copy(cards = it.cards + card, selectedCardId = card.id, message = "명함을 만들었습니다. 아직 상대에게 보내지 않았습니다.") }
        onSuccess()
    }
    fun send(cardId: String, recipient: String, context: ExchangeContextModel, onSuccess: () -> Unit) = action {
        wallet.send(cardId, recipient, context, requireNotNull(profiles.accountId))
        mutable.update { it.copy(message = "서버가 명함 전달을 확인했습니다. 열람 여부는 알 수 없습니다.") }
        onSuccess()
        // Reciprocal is only read from the server, never optimistically flipped.
        val wallet = wallet.wallet()
        mutable.update { it.copy(wallet = wallet) }
    }
}
