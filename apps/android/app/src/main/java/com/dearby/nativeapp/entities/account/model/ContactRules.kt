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
     * 연락처 바로가기 (2026-10-26 사용자 결정 "연락처 아이콘·바로가기"): what a contact opens. A value that is already an `http(s)://` address opens as it is;
     * GitHub·Instagram·Behance ids (`@id`, `id`, `github.com/id`) become their profile pages; phone → dialer, email → mail. A 카카오톡 value opens only as
     * an address (`open.kakao.com/…`); otherwise it is an ID and null here ([copyText] copies it).
     */
    fun link(kind: String, value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val url = text.takeIf { Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        return when (kind) {
            "phone" -> "tel:" + text.filter { it.isDigit() || it == '+' }
            "email" -> "mailto:$text"
            "github" -> url ?: profileUrl("github.com", text)
            "instagram" -> url ?: profileUrl("instagram.com", text)
            "behance" -> url ?: profileUrl("www.behance.net", text)
            "kakao" -> url ?: text.takeIf { it.startsWith("open.kakao.com/", ignoreCase = true) }?.let { "https://$it" }
            else -> url
        }
    }
    /** The 카카오톡 ID to copy when [link] has no address for it. */
    fun copyText(kind: String, value: String): String? = value.trim().removePrefix("@").takeIf { kind == "kakao" && it.isNotEmpty() && link(kind, value) == null }
    private fun profileUrl(host: String, value: String): String {
        val bare = host.removePrefix("www.")
        val path = value.removePrefix("www.").let { if (it.startsWith("$bare/", ignoreCase = true)) it.substring(bare.length + 1) else it }.trim('/').removePrefix("@")
        return "https://$host/$path"
    }
    /** `2026.03 – 2026.06`, or `2026.03 – 진행 중` without an end date. Dates are `YYYY-MM-DD`. */
    fun period(start: String, end: String?): String {
        fun month(date: String) = date.take(7).replace('-', '.')
        return month(start) + " – " + (end?.let(::month) ?: "진행 중")
    }
}
