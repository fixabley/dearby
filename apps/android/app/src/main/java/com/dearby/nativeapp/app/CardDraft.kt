package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.AccountContact
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.widgets.card.cardContent.CardComposerContact
import com.dearby.nativeapp.widgets.card.cardContent.CardComposerHistory
import java.util.UUID

private val contactKinds = listOf("phone" to "전화번호", "email" to "이메일", "kakao" to "카카오톡",
    "instagram" to "인스타그램", "github" to "GitHub", "behance" to "Behance")

/** Composer state: the private profile plus which contacts and histories the new card shows. */
data class CardDraft(
    val name: String = "", val job: String = "", val introduction: String = "",
    val contacts: List<CardComposerContact> = withEmptyRows(emptyList()),
    val histories: List<CardComposerHistory> = emptyList(),
    val saved: List<AccountHistory> = emptyList(),
) {
    private val filled get() = contacts.filter { it.value.isNotBlank() }
    val canPublish get() = name.isNotBlank()
    /** `PUT /v1/profile` body: filled contacts only, saved histories unchanged. */
    val profile get() = AccountProfile(name.trim(), job, introduction,
        filled.map { AccountContact(it.id, it.kind, it.label, it.value.trim()) }, saved)
    val contactIds get() = filled.filter { it.isPublic }.map { it.id }
    val historyIds get() = histories.filter { it.isPublic }.map { it.id }

    /**
     * After signing in: keep what is on screen and the account's other saved data, so publishing never
     * drops stored contacts or histories. Saved rows that were not on screen stay private.
     */
    fun merged(stored: AccountProfile): CardDraft {
        val base = from(stored)
        val shown = contacts.associateBy { it.id }
        val rows = base.contacts.map { if (it.value.isEmpty()) it else it.copy(isPublic = shown[it.id]?.isPublic ?: false) }.toMutableList()
        for (typed in filled) {
            val index = rows.indexOfFirst { it.id == typed.id }.takeIf { it >= 0 }
                ?: rows.indexOfFirst { it.kind == typed.kind && it.value.isEmpty() }.takeIf { it >= 0 }
            if (index == null) rows += typed else rows[index] = rows[index].copy(value = typed.value, isPublic = typed.isPublic)
        }
        val shownHistories = histories.associateBy { it.id }
        return base.copy(name = name.ifBlank { base.name }, job = job.ifEmpty { base.job },
            introduction = introduction.ifEmpty { base.introduction }, contacts = rows,
            histories = base.histories.map { it.copy(isPublic = shownHistories[it.id]?.isPublic ?: false) })
    }

    companion object {
        /** Saved contacts first, then one empty row for each kind the profile does not have yet. */
        fun from(profile: AccountProfile) = CardDraft(profile.name, profile.job, profile.introduction,
            withEmptyRows(profile.contacts.map { CardComposerContact(it.id, it.kind, it.label, it.value, true) }),
            profile.histories.map { CardComposerHistory(it.id, it.title, listOf(it.role, it.startDate).filter(String::isNotEmpty).joinToString(" · "), true) },
            profile.histories)
    }
}

private fun withEmptyRows(existing: List<CardComposerContact>) = existing + contactKinds.filter { (kind, _) -> existing.none { it.kind == kind } }
    .map { (kind, label) -> CardComposerContact(UUID.randomUUID().toString(), kind, label, "", true) }
