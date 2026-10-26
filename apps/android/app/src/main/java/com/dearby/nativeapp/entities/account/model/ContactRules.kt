package com.dearby.nativeapp.entities.account.model

/** Contract #149: a profile needs a phone and an email. Same rules as `PUT /v1/profile`. */
object ContactRules {
    /** Digits, `+`, `-` and spaces only, with 8 to 15 digits. */
    fun validPhone(value: String): Boolean {
        val text = value.trim()
        return text.isNotEmpty() && text.all { it in '0'..'9' || it in "+- " } && text.count { it in '0'..'9' } in 8..15
    }
    /** A basic `name@domain.tld` shape; the confirmation is that mail arrives. */
    fun validEmail(value: String) = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(value.trim())
    /**
     * 연락처 바로가기 (contract "연락처 아이콘·바로가기", 2026-10-26): what a contact opens. An `http(s)` URL opens as it is, `domain/path` gets `https://`
     * (other schemes never open); GitHub·Instagram·Behance ids (one leading `@` dropped, letters·digits·`.`·`_`·`-` only) become their profile pages;
     * phone → `tel:`, email → `mailto:`. A 카카오톡 value that is not an address is an ID: null here, [copyText] copies it.
     */
    fun link(kind: String, value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (kind == "phone") return "tel:" + text.filter { it.isDigit() || it == '+' }
        if (kind == "email") return "mailto:$text"
        val url = when {
            Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(text) -> text
            ':' !in text && text.substringBefore('/').contains('.') -> "https://$text"
            else -> null
        }
        val id = text.removePrefix("@").takeIf { url == null && Regex("^[A-Za-z0-9._-]+$").matches(it) }
        return url ?: id?.let { when (kind) {
            "github" -> "https://github.com/$it"
            "instagram" -> "https://instagram.com/$it"
            "behance" -> "https://www.behance.net/$it"
            else -> null
        } }
    }
    /** The 카카오톡 ID to copy (one leading `@` dropped) when [link] has no address for it. */
    fun copyText(kind: String, value: String): String? = value.trim().removePrefix("@").takeIf { kind == "kakao" && it.isNotEmpty() && link(kind, value) == null }
    /** `2026.03 – 2026.06`, or `2026.03 – 진행 중` without an end date. Dates are `YYYY-MM-DD`. */
    fun period(start: String, end: String?): String {
        fun month(date: String) = date.take(7).replace('-', '.')
        return month(start) + " – " + (end?.let(::month) ?: "진행 중")
    }
}
