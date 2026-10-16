package io.wid.dearby.application.card

import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.Profile
import java.util.*

// 명함 꾸미기 템플릿을 채운다(native-v1.md "템플릿"). {{profile.name}}·{{profile.job}}·{{profile.introduction}}·{{contact:<id>}}만 바꾸고
// 나머지 마크다운은 그대로 둔다. 연락처는 그 명함에 고른 것만 채우고, 모르는 토큰·고르지 않은 연락처는 빈 문자열이 된다(원문 토큰을 남기지 않는다)
object TemplateRenderer {

    private val TOKEN = Regex("\\{\\{\\s*([a-zA-Z]+)(?:[.:]([^{}\\s]+))?\\s*}}")

    fun render(template: String, profile: Profile, selectedContacts: List<Contact>): String {
        val contacts = selectedContacts.associateBy { it.id.toString().lowercase() }
        return TOKEN.replace(template) { match ->
            val (kind, key) = match.groupValues.let { it[1] to it[2] }
            when (kind) {
                "profile" -> when (key) {
                    "name" -> profile.name
                    "job" -> profile.job
                    "introduction" -> profile.introduction
                    else -> ""
                }
                "contact" -> contacts[key.lowercase()]?.value ?: ""
                else -> ""
            }
        }
    }

    // 템플릿이 가리키는 연락처 id(명함에 고른 것과 맞춰 보는 용도)
    fun contactIds(template: String): Set<UUID> =
        TOKEN.findAll(template).filter { it.groupValues[1] == "contact" }
            .mapNotNull { runCatching { UUID.fromString(it.groupValues[2]) }.getOrNull() }.toSet()
}
