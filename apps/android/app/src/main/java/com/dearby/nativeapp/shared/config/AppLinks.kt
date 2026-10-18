package com.dearby.nativeapp.shared.config

import java.net.URI
import java.util.UUID

/** Where Apple's web flow returns (contract "애플 로그인"): `dearby://auth/apple?code=<UUID>` or `?error=cancelled|failed`. */
sealed interface AppleRedirect {
    data class Code(val code: String) : AppleRedirect
    data object Cancelled : AppleRedirect
    data object Failed : AppleRedirect
}

/** Exactly `dearby://auth/apple` with one `code=<UUID>` or `error=cancelled|failed`; anything else is not Apple's redirect. */
fun appleRedirect(link: String): AppleRedirect? = runCatching {
    val uri = URI(link)
    if (uri.scheme != "dearby" || uri.rawAuthority != "auth" || uri.rawPath != "/apple" || uri.rawFragment != null) return null
    val query = uri.rawQuery?.split('&') ?: return null
    if (query.size != 1) return null
    val (name, value) = query[0].split('=', limit = 2).takeIf { it.size == 2 } ?: return null
    when (name) {
        "code" -> AppleRedirect.Code(UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) } ?: return null)
        "error" -> when (value) { "cancelled" -> AppleRedirect.Cancelled; "failed" -> AppleRedirect.Failed; else -> null }
        else -> null
    }
}.getOrNull()
