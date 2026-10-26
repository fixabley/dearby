package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.widgets.card.cardContent.ComposerContact
import com.dearby.nativeapp.widgets.card.cardContent.ComposerHistory

/**
 * Composer state: the saved profile's data and which contacts, histories, photo and 소개 링크 the card shows (there is no body since 2026-10-26,
 * contract "소개 링크·소개 글 폐지").
 */
data class CardDraft(
    val profile: AccountProfile, val cardTitle: String, val publicContactIds: Set<String>, val publicHistoryIds: Set<String>, val includePhoto: Boolean, val includeIntroLink: Boolean, val initial: Initial,
) {
    data class Initial(val cardTitle: String, val contacts: Set<String>, val histories: Set<String>, val includePhoto: Boolean, val includeIntroLink: Boolean)
    val changed get() = Initial(cardTitle, publicContactIds, publicHistoryIds, includePhoto, includeIntroLink) != initial
    fun revert() = copy(cardTitle = initial.cardTitle, publicContactIds = initial.contacts, publicHistoryIds = initial.histories, includePhoto = initial.includePhoto, includeIntroLink = initial.includeIntroLink)
    /** 사진 넣기 (contract "프로필 사진"): the switch shows only when the profile has a photo; without one the server makes the card without, whatever is sent. */
    val hasPhoto get() = profile.photoUrl != null
    /** 소개 링크 넣기: the switch shows only when the profile has a link. */
    val hasIntroLink get() = profile.introLink != null
    val cardName get() = cardTitle.trim().ifEmpty { "내 명함" }
    /** A new card left unnamed is "내 명함", or "내 명함 2"·"내 명함 3"… when [taken] (the account's card names) already has it. */
    fun newCardName(taken: Collection<String>) = if (cardTitle.isNotBlank()) cardName
        else generateSequence(1) { it + 1 }.map { if (it == 1) cardName else "$cardName $it" }.first { it !in taken }
    val contactIds get() = profile.contacts.map { it.id }.filter { it in publicContactIds }
    val historyIds get() = profile.histories.map { it.id }.filter { it in publicHistoryIds }
    val contacts get() = profile.contacts.map { ComposerContact(it.id, it.kind, ContactRules.shown(it.kind, it.value), it.id in publicContactIds) }
    val histories get() = profile.histories.map {
        ComposerHistory(it.id, it.chain.joinToString(" · "), listOf(it.roleTitle, ContactRules.period(it.startDate, it.endDate)).filter(String::isNotEmpty).joinToString(" · "),
            it.id in publicHistoryIds, if (it.isApproved) null else if (it.approval == "rejected") "거절된 이력은 명함에 넣을 수 없어요" else "관리자 승인 뒤 명함에 넣을 수 있어요")
    }
    /** Why the profile cannot be published yet (name, phone and email are required data), or null. */
    val problem get() = when {
        profile.name.isBlank() -> "프로필에서 이름을 먼저 입력해 주세요."
        profile.contacts.none { it.kind == "phone" } || profile.contacts.none { it.kind == "email" } -> "프로필에서 전화번호와 이메일을 먼저 입력해 주세요."
        else -> null
    }
    companion object {
        /** A new card starts with every contact and approved history public and the photo in; a republish starts from that card's name and selections. */
        fun from(profile: AccountProfile, prefill: PublishedCard? = null): CardDraft {
            val approved = profile.histories.filter { it.isApproved }.map { it.id }.toSet()
            val title = prefill?.name.orEmpty()
            val contacts = prefill?.contacts?.map { it.id }?.toSet() ?: profile.contacts.map { it.id }.toSet()
            val histories = prefill?.histories?.map { it.id }?.toSet()?.intersect(approved) ?: approved
            // 고치기 starts from what the card shows now: a card without a photo stays without unless the switch is turned on.
            val photo = prefill?.let { it.photoUrl != null } ?: true
            val introLink = prefill?.let { it.introLink != null } ?: true
            return CardDraft(profile, title, contacts, histories, photo, introLink, Initial(title, contacts, histories, photo, introLink))
        }
    }
}
