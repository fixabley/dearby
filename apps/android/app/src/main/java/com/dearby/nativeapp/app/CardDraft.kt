package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.widgets.card.cardContent.ComposerContact
import com.dearby.nativeapp.widgets.card.cardContent.ComposerHistory

/**
 * Composer state: the saved profile's data, the decoration template and which contacts and histories the card shows.
 * [webTemplate] is the optional web version (contract "웹 버전"); null means the card has none and the web shows the mobile body.
 */
data class CardDraft(
    val profile: AccountProfile, val cardTitle: String, val template: String, val publicContactIds: Set<String>, val publicHistoryIds: Set<String>,
    val initial: Initial, val webTemplate: String? = null,
) {
    data class Initial(val cardTitle: String, val template: String, val contacts: Set<String>, val histories: Set<String>, val webTemplate: String? = null)
    val changed get() = Initial(cardTitle, template, publicContactIds, publicHistoryIds, webTemplate) != initial
    fun revert() = copy(cardTitle = initial.cardTitle, template = initial.template, publicContactIds = initial.contacts, publicHistoryIds = initial.histories, webTemplate = initial.webTemplate)
    /** Contacts named in either template. */
    val tokenContactIds get() = TemplateDocument.contactIds(template) + (webTemplate?.let(TemplateDocument::contactIds) ?: emptySet())
    /** Contacts the template names are always public (the server leaves unselected tokens empty). */
    val effectiveContactIds: Set<String> get() {
        val fromTokens: List<String> = profile.contacts.filter { it.id.lowercase() in tokenContactIds }.map { it.id }
        return publicContactIds + fromTokens
    }
    val preview get() = TemplateDocument.fill(template, profile, effectiveContactIds)
    val webPreview get() = webTemplate?.let { TemplateDocument.fill(it, profile, effectiveContactIds) }
    val cardName get() = cardTitle.trim().ifEmpty { "내 명함" }
    val contactIds get() = profile.contacts.map { it.id }.filter { it in effectiveContactIds }
    val historyIds get() = profile.histories.map { it.id }.filter { it in publicHistoryIds }
    val contacts get() = profile.contacts.map { ComposerContact(it.id, it.label, it.value, it.id in effectiveContactIds, it.id.lowercase() in tokenContactIds) }
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
        /** A new card starts from the starter template with every contact and approved history public; a republish starts from that card. */
        fun from(profile: AccountProfile, prefill: PublishedCard? = null): CardDraft {
            val approved = profile.histories.filter { it.isApproved }.map { it.id }.toSet()
            val title = prefill?.name.orEmpty()
            val template = prefill?.template ?: TemplateDocument.starter(profile)
            val webTemplate = prefill?.webTemplate?.takeIf { it.isNotBlank() }
            val contacts = prefill?.contacts?.map { it.id }?.toSet() ?: profile.contacts.map { it.id }.toSet()
            val histories = prefill?.histories?.map { it.id }?.toSet()?.intersect(approved) ?: approved
            return CardDraft(profile, title, template, contacts, histories, Initial(title, template, contacts, histories, webTemplate), webTemplate)
        }
    }
}
