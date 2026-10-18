package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.WalletReceipt
import com.dearby.nativeapp.pages.wallet.ReceivedCard
import com.dearby.nativeapp.pages.wallet.WalletPhase
import com.dearby.nativeapp.pages.wallet.WalletState
import com.dearby.nativeapp.shared.config.CardLink
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 받은 명함 from `GET /v1/wallet`, plus the two exchange actions of the contract "명함 교환": saving a card that arrived as a
 * `dearby://cards/…` link ([receive]) and handing one of mine back ([reciprocate]). Signed out, there is nothing to show.
 */
class WalletViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(WalletState())
    val state = mutable.asStateFlow()
    /** A card link waiting for a signed-in account. */
    private var pending: CardLink? = null

    fun load() = viewModelScope.launch { reload() }
    private suspend fun reload() {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.update { it.copy(phase = WalletPhase.SIGNED_OUT, cards = emptyList()) }; return }
        if (mutable.value.cards.isEmpty()) mutable.update { it.copy(phase = WalletPhase.LOADING) }
        try {
            val wallet = account.authorized { account.client.wallet(it) }
            mutable.update { it.copy(phase = WalletPhase.LOADED, cards = wallet.items.map { receipt -> receipt.toReceived() }) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = if (e.error == AccountError.UNAUTHORIZED) WalletPhase.SIGNED_OUT else WalletPhase.FAILED) }
        }
    }

    /** The app was opened with `dearby://cards/<id>?activity=<n>`: save it to the account wallet, or ask to sign in first. */
    fun receive(link: CardLink) { pending = link; viewModelScope.launch { savePending() } }
    /** The user signed in from the prompt: save the waiting link now. */
    fun retryPending() = viewModelScope.launch { savePending() }
    fun dismissSignIn() { pending = null; mutable.update { it.copy(needSignIn = false) } }
    fun consumeOpened() = mutable.update { it.copy(opened = null) }
    fun dismissNotice() = mutable.update { it.copy(notice = null) }
    private suspend fun savePending() {
        val link = pending ?: return
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.update { it.copy(needSignIn = true) }; return }
        mutable.update { it.copy(needSignIn = false) }
        val notice = try {
            val receipt = account.authorized { account.client.saveReceipt(link.cardId, link.activityId, it) }
            pending = null
            reload()
            mutable.update { it.copy(opened = receipt.id) }
            return
        } catch (e: AccountException) {
            when (e.error) {
                AccountError.UNAUTHORIZED -> { mutable.update { it.copy(needSignIn = true) }; return }
                AccountError.CONFLICT -> "내 명함이에요."
                AccountError.NOT_FOUND -> "지워진 명함이라 저장할 수 없어요."
                else -> "명함을 저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
            }
        }
        pending = null
        mutable.update { it.copy(notice = notice) }
    }

    /** 내 명함도 건네기: the owner of the received [cardId] gets [myCardId] in the same activity; the receipt becomes 맞교환. */
    fun reciprocate(cardId: String, myCardId: String) = viewModelScope.launch {
        mutable.update { it.copy(busy = true, message = null) }
        try {
            account.authorized { account.client.reciprocate(cardId, myCardId, it) }
            mutable.update { s -> s.copy(busy = false, cards = s.cards.map { if (it.card.id == cardId) it.copy(reciprocal = true, badge = "맞교환") else it }) }
        } catch (e: AccountException) {
            mutable.update { it.copy(busy = false, message = "명함을 건네지 못했어요. 연결을 확인하고 다시 시도해 주세요.") }
        }
    }
}

private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
private val dayText = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/** Badge per the contract: `맞교환`, `오늘 사라져요`, `N일 뒤 사라져요` from the server's `expiresAt`; null when neither is known. */
private fun WalletReceipt.toReceived(): ReceivedCard {
    val expires = expiresAt?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(seoul).toLocalDate() }.getOrNull() }
    val badge = when {
        reciprocal -> "맞교환"
        expires != null -> ChronoUnit.DAYS.between(LocalDate.now(seoul), expires).let { if (it <= 0) "오늘 사라져요" else "${it}일 뒤 사라져요" }
        else -> null
    }
    val received = runCatching { OffsetDateTime.parse(receivedAt).atZoneSameInstant(seoul).format(dayText) }.getOrDefault(receivedAt.take(10))
    return ReceivedCard(id, card.toState(), context.programName, context.activityName, received, reciprocal, badge)
}

fun PublishedCard.toState() = CardState(id, profileName, job, name, description, introduction,
    contacts.map { ContactState(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.chain.joinToString(" · "), it.roleTitle, ContactRules.period(it.startDate, it.endDate), it.isVerified, it.description) },
    // 목록 칩용 소속: 승인된 이력 중 진행 중인 것, 그다음 시작일이 늦은 것(contract "받은 명함 목록 표시")
    organization = histories.filter { it.isApproved }.sortedWith(compareBy<AccountHistory> { it.endDate != null }.thenByDescending { it.startDate })
        .firstNotNullOfOrNull { it.organization?.name ?: it.organizationName }?.takeIf { it.isNotBlank() })
