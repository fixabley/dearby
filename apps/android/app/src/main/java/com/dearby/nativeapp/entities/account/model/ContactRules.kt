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
    /** `2026.03 – 2026.06`, or `2026.03 – 진행 중` without an end date. Dates are `YYYY-MM-DD`. */
    fun period(start: String, end: String?): String {
        fun month(date: String) = date.take(7).replace('-', '.')
        return month(start) + " – " + (end?.let(::month) ?: "진행 중")
    }
}
