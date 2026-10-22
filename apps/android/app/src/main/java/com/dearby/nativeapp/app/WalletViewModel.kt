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
import com.dearby.nativeapp.pages.wallet.SharedCard
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
 * A card App Link ([view]) opens the card's 받은 명함 detail, or 공유받은 명함 보기 whose `받은 명함에 저장` is [receive];
 * [activityName] reads `<프로그램> <활동>` for its origin line (null when the activity is gone or unreadable).
 */
class WalletViewModel(private val account: AccountViewModel, private val activityName: suspend (Long) -> String? = { null }) : ViewModel() {
    private val mutable = MutableStateFlow(WalletState())
    val state = mutable.asStateFlow()
    /** A card link waiting for a signed-in account. */
    private var pending: CardLink? = null
    /** The card App Link 공유받은 명함 보기 shows. */
    private var viewing: CardLink? = null

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

    /** The app was opened with `dearby://cards/<id>?activity=<n>&code=<code>`: save it to the account wallet, or ask to sign in first. */
    fun receive(link: CardLink) { pending = link; closeShared(); viewModelScope.launch { savePending() } }
    /**
     * The app was opened with the App Link `https://<web origin>/cards/<id>?…` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): a card
     * the signed-in wallet already holds opens its 받은 명함 detail (no save); otherwise 공유받은 명함 보기 reads the public card.
     */
    fun view(link: CardLink) = viewModelScope.launch {
        val signedIn = account.state.value.phase == AccountPhase.SIGNED_IN
        if (signedIn) {
            reload()
            mutable.value.cards.firstOrNull { it.card.id == link.cardId }?.let { held -> closeShared(); mutable.update { it.copy(opened = held.id) }; return@launch }
        }
        viewing = link
        mutable.update { it.copy(shared = SharedCard()) }
        val shared = try {
            val card = if (signedIn) account.authorized { account.client.publicCard(link.cardId, link.code, it) } else account.client.publicCard(link.cardId, link.code, null)
            val own = signedIn && account.authorized { account.client.cards(it) }.any { it.id == link.cardId }
            SharedCard(card.toState(), link.activityId?.let { activityName(it) }, own)
        } catch (e: AccountException) {
            SharedCard(error = when (e.error) {
                AccountError.FORBIDDEN -> "명함 주인에게 QR을 다시 보여 달라고 하세요."
                AccountError.NOT_FOUND -> "지워진 명함이라 저장할 수 없어요."
                else -> "명함을 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요."
            })
        }
        // A newer link or a close meanwhile wins.
        if (viewing == link) mutable.update { it.copy(shared = shared) }
    }
    /** 받은 명함에 저장 on 공유받은 명함 보기: the `dearby://cards/…` save flow for the viewed link (sign-in first when signed out). */
    fun saveShared() { pending = viewing; viewModelScope.launch { savePending() } }
    fun closeShared() { viewing = null; mutable.update { it.copy(shared = null) } }
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
            val receipt = account.authorized { account.client.saveReceipt(link.cardId, link.activityId, link.code, it) }
            pending = null
            reload()
            closeShared()
            mutable.update { it.copy(opened = receipt.id) }
            return
        } catch (e: AccountException) {
            when (e.error) {
                AccountError.UNAUTHORIZED -> { mutable.update { it.copy(needSignIn = true) }; return }
                AccountError.CONFLICT -> if (e.code == "BLOCKED") "차단한 사람의 명함이에요." else "내 명함이에요."
                AccountError.NOT_FOUND -> "지워진 명함이라 저장할 수 없어요."
                AccountError.FORBIDDEN -> "명함 주인에게 QR을 다시 보여 달라고 하세요."
                else -> "명함을 저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
            }
        }
        pending = null
        mutable.update { it.copy(notice = notice) }
    }

    /** 신고하기 (contract "명함 신고·차단"): the card stays; the result is a notice. */
    fun report(cardId: String, reason: String, detail: String?) = viewModelScope.launch {
        // 공유받은 명함 보기 may be open signed out; reporting needs an account.
        if (account.state.value.phase != AccountPhase.SIGNED_IN) return@launch mutable.update { it.copy(notice = "로그인한 뒤 신고할 수 있어요.") }
        mutable.update { it.copy(busy = true, message = null) }
        val notice = try {
            account.authorized { account.client.reportCard(cardId, reason, detail, it) }
            "신고를 받았어요. 24시간 안에 확인할게요."
        } catch (e: AccountException) { "신고를 보내지 못했어요. 연결을 확인하고 다시 시도해 주세요." }
        mutable.update { it.copy(busy = false, notice = notice) }
    }
    /** 삭제하고 차단하기: every card of that owner leaves the list, which also closes the open detail. */
    fun block(cardId: String) = viewModelScope.launch {
        mutable.update { it.copy(busy = true, message = null) }
        try {
            account.authorized { account.client.block(cardId, it) }
            reload()
            mutable.update { it.copy(busy = false) }
        } catch (e: AccountException) {
            mutable.update { it.copy(busy = false, message = "차단하지 못했어요. 연결을 확인하고 다시 시도해 주세요.") }
        }
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
