package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.AccountProfile

/**
 * The built-in card template (contract "템플릿", 2026-10-18 앱 안 꾸미기 삭제): markdown with `{{profile.name}}`, `{{profile.job}}`,
 * `{{profile.introduction}}` and `{{contact:<id>}}`. The app shows it filled in as a read-only preview; the server fills it at publish.
 */
object TemplateDocument {
    private val pattern = Regex("""\{\{\s*([a-zA-Z]+)(?:[.:]([^{}\s]+))?\s*}}""")

    /**
     * The starter template for a new card: the self-introduction only. Name, job and contacts are drawn by the card face itself
     * (contract "명함 표현"), so repeating them here showed the same text twice (2026-10-18).
     */
    fun starter(profile: AccountProfile): String = "{{profile.introduction}}"
    /** Contact ids the template refers to; they are always among the card's public contacts. */
    fun contactIds(template: String): Set<String> =
        pattern.findAll(template).filter { it.groupValues[1] == "contact" }.map { it.groupValues[2].lowercase() }.toSet()
    /** The template with tokens replaced: profile fields, only the selected contacts, "" for everything else. */
    fun fill(template: String, profile: AccountProfile, contactIds: Set<String>): String {
        val contacts = profile.contacts.filter { it.id in contactIds }.associate { it.id.lowercase() to it.value }
        return pattern.replace(template) { match ->
            when (match.groupValues[1] to match.groupValues[2]) {
                "profile" to "name" -> profile.name
                "profile" to "job" -> profile.job
                "profile" to "introduction" -> profile.introduction
                else -> if (match.groupValues[1] == "contact") contacts[match.groupValues[2].lowercase()].orEmpty() else ""
            }
        }
    }
}
