package com.dearby.nativeapp.features.contact

import java.net.URI

data class ContactActionState(val id: String, val kind: String, val label: String, val value: String, val target: String?)
fun contactAction(id: String, kind: String, label: String, value: String): ContactActionState {
    val title = label.ifBlank { when (kind) { "phone" -> "전화"; "email" -> "이메일"; "kakao" -> "카카오톡"; "instagram" -> "인스타그램"; "github" -> "GitHub"; "behance" -> "Behance"; else -> "연락처" } }
    val target = when (kind) {
        "phone" -> value.replace(Regex("[\\s()\\-]"), "").takeIf { it.matches(Regex("\\+?[0-9]{3,20}")) }?.let { "tel:$it" }
        "email" -> value.takeIf { it.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")) }?.let { "mailto:$it" }
        "kakao", "instagram", "github", "behance" -> runCatching { URI(value) }.getOrNull()?.takeIf { it.scheme == "https" && it.host != null && it.userInfo == null && it.port in setOf(-1, 443) }?.toASCIIString()
        else -> null
    }
    return ContactActionState(id, kind, title, value, target)
}
