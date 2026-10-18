package com.dearby.nativeapp.shared.config

import java.net.URI
import java.util.UUID

/** A sign-in provider with a web flow (contract "애플 로그인", "구글 로그인"). [path] is the API segment and the redirect path. */
enum class AuthProvider(val path: String, val label: String) { APPLE("apple", "애플"), GOOGLE("google", "Google") }

/** Where a provider's web flow returns: `dearby://auth/<apple|google>?code=<UUID>` or `?error=cancelled|failed`. */
sealed interface AuthRedirect {
    val provider: AuthProvider
    data class Code(override val provider: AuthProvider, val code: String) : AuthRedirect
    data class Cancelled(override val provider: AuthProvider) : AuthRedirect
    data class Failed(override val provider: AuthProvider) : AuthRedirect
}

/** Exactly `dearby://auth/<provider>` with one `code=<UUID>` or `error=cancelled|failed`; anything else is not a sign-in redirect. */
fun authRedirect(link: String): AuthRedirect? = runCatching {
    val uri = URI(link)
    if (uri.scheme != "dearby" || uri.rawAuthority != "auth" || uri.rawFragment != null) return null
    val provider = AuthProvider.entries.firstOrNull { "/${it.path}" == uri.rawPath } ?: return null
    val query = uri.rawQuery?.split('&') ?: return null
    if (query.size != 1) return null
    val (name, value) = query[0].split('=', limit = 2).takeIf { it.size == 2 } ?: return null
    when (name) {
        "code" -> AuthRedirect.Code(provider, UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) } ?: return null)
        "error" -> when (value) { "cancelled" -> AuthRedirect.Cancelled(provider); "failed" -> AuthRedirect.Failed(provider); else -> null }
        else -> null
    }
}.getOrNull()
