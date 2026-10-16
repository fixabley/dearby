package com.dearby.nativeapp.shared.config

import java.net.URI
import java.util.UUID

/** The link a share's QR carries: `<web origin>/s/<share ID>`. */
fun sharedCardUrl(shareId: String, webOrigin: String) = "$webOrigin/s/$shareId"

/** Exactly `<web origin>/s/<UUID>` → lower-case share ID; query, fragment or anything else is ignored. */
fun sharedCardId(link: String, webOrigin: String): String? = runCatching {
    val uri = URI(link)
    val web = URI(webOrigin)
    if (uri.scheme != web.scheme || uri.host?.lowercase() != web.host || uri.port != web.port) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null
    val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    if (path.size != 2 || path[0] != "s") return null
    // UUID.fromString accepts short groups, so require the canonical form back.
    UUID.fromString(path[1]).toString().takeIf { it.equals(path[1], ignoreCase = true) }
}.getOrNull()

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
