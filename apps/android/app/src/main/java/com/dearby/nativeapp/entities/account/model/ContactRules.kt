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
     * 연락처 바로가기 (contract "연락처 아이콘·바로가기", 2026-10-26): what a contact opens. phone → `tel:`, email → `mailto:`, a GitHub·Instagram·Behance
     * username ([username]) → its profile page. A 카카오톡 ID has no link ([copyText] copies it); an open-chat URL saved before 2026-10-26 still opens
     * (`http(s)` as it is, `domain/path` gets `https://`, other schemes never open).
     */
    fun link(kind: String, value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (kind == "phone") return "tel:" + text.filter { it.isDigit() || it == '+' }
        if (kind == "email") return "mailto:$text"
        val host = usernameRules[kind]?.host ?: return web(text)
        return username(kind, text)?.let { "https://" + (if (kind == "behance") "www." else "") + host + "/" + it }
    }
    private fun web(text: String): String? = when {
        Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(text) -> text
        ':' !in text && '/' in text && text.substringBefore('/').contains('.') -> "https://$text"
        else -> null
    }
    /** The 카카오톡 ID to copy when [link] has no address for it. */
    fun copyText(kind: String, value: String): String? =
        (username(kind, value) ?: value.trim().removePrefix("@")).takeIf { kind == "kakao" && it.isNotEmpty() && link(kind, value) == null }

    /** 연락처 사용자명 입력 (contract "연락처 사용자명 입력", 2026-10-26): the site the app builds the link on (카카오톡 none) and the longest username. Same table as `PUT /v1/profile`. */
    private class UsernameRule(val host: String?, val max: Int)
    private val usernameRules = mapOf(
        "github" to UsernameRule("github.com", 39), "instagram" to UsernameRule("instagram.com", 30),
        "behance" to UsernameRule("behance.net", 64), "kakao" to UsernameRule(null, 20),
    )
    /** The fixed text before a username field (`github.com/`); null for other kinds and 카카오톡. */
    fun usernamePrefix(kind: String): String? = usernameRules[kind]?.host?.let { "$it/" }
    /**
     * The username in [text] — a pasted profile URL (`https://github.com/name`) gives its first path part, `@` and spaces are dropped — or null when
     * what is left is empty, too long or has characters other than 영문·숫자·`.`·`_`·`-`.
     */
    fun username(kind: String, text: String): String? {
        val rule = usernameRules[kind] ?: return null
        val id = profilePath(text, rule.host).filterNot { it.isWhitespace() || it == '@' }
        return id.takeIf { it.isNotEmpty() && it.length <= rule.max && it.all(::isUsernameChar) }
    }
    /** What typing or pasting leaves in a username field: its allowed characters up to the length; a URL in the 카카오톡 field is no ID and leaves it empty. */
    fun sanitizedUsername(kind: String, text: String): String {
        val rule = usernameRules[kind] ?: return text
        if (rule.host == null && '/' in text) return ""
        return profilePath(text, rule.host).filter(::isUsernameChar).take(rule.max)
    }
    /** The value as rows show it: the username for username kinds (also for a profile URL saved before 2026-10-26), otherwise as saved. */
    fun shown(kind: String, value: String) = username(kind, value) ?: value
    private fun isUsernameChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "._-"
    /** `[https://][www.|m.]host/name[/…]` → `name`; anything else as it is. */
    private fun profilePath(text: String, host: String?): String {
        val trimmed = text.trim()
        val match = host?.let { Regex("^(https?://)?((www|m)\\.)?" + it.replace(".", "\\.") + "/", RegexOption.IGNORE_CASE).find(trimmed) } ?: return trimmed
        return trimmed.substring(match.range.last + 1).takeWhile { it !in "/?#" }
    }
    /** `2026.03 – 2026.06`, or `2026.03 – 진행 중` without an end date. Dates are `YYYY-MM-DD`. */
    fun period(start: String, end: String?): String {
        fun month(date: String) = date.take(7).replace('-', '.')
        return month(start) + " – " + (end?.let(::month) ?: "진행 중")
    }
}
