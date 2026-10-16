package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.widgets.card.cardContent.TemplateToken

/**
 * The card decoration template (contract "템플릿"): markdown with `{{profile.name}}`, `{{profile.job}}`,
 * `{{profile.introduction}}` and `{{contact:<id>}}`. The app shows it filled in; the server fills it at publish.
 */
object TemplateDocument {
    private val pattern = Regex("""\{\{\s*([a-zA-Z]+)(?:[.:]([^{}\s]+))?\s*}}""")

    /** The starter template for a new card: name, job, introduction and the phone and email tokens. */
    fun starter(profile: AccountProfile): String =
        (listOf("# {{profile.name}}", "{{profile.job}}", "", "{{profile.introduction}}", "") +
            profile.contacts.filter { it.kind == "phone" || it.kind == "email" }.map { "- ${it.label}: {{contact:${it.id}}}" }).joinToString("\n")
    fun tokens(profile: AccountProfile): List<TemplateToken> =
        listOf(TemplateToken("profile.name", "이름", profile.name), TemplateToken("profile.job", "직함", profile.job), TemplateToken("profile.introduction", "소개", profile.introduction)) +
            profile.contacts.map { TemplateToken("contact:${it.id}", it.label, it.value) }
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
