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

/** A received card link (contract "명함 교환"): `dearby://cards/<cardId>?activity=<n>`; [activityId] is the activity's numeric id or null. */
data class CardLink(val cardId: String, val activityId: Long?)

/** Exactly `dearby://cards/<UUID>` with an optional numeric `activity`; anything else is not a card link. */
fun cardLink(link: String): CardLink? = runCatching {
    val uri = URI(link)
    if (uri.scheme != "dearby" || uri.rawAuthority != "cards" || uri.rawFragment != null) return null
    val id = uri.rawPath.removePrefix("/").takeIf { it.isNotEmpty() && '/' !in it } ?: return null
    val cardId = UUID.fromString(id).toString().takeIf { it.equals(id, ignoreCase = true) } ?: return null
    val activity = uri.rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
        pair.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "activity" }?.get(1)?.toLongOrNull()
    }
    CardLink(cardId, activity)
}.getOrNull()

/** What the 내 명함 QR encodes: the public card page, with the activity the card is handed out at when there is one. */
fun cardQrUrl(webOrigin: String, cardId: String, activityId: String?): String =
    "${webOrigin.trimEnd('/')}/cards/$cardId" + (activityId?.let { "?activity=$it" } ?: "")
