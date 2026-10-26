package com.dearby.nativeapp.shared.config

import java.net.URI
import java.util.UUID

/** A sign-in provider (contract "애플 로그인", "구글 로그인"). [path] is the API segment; only Apple's web flow uses it as a redirect path (Google is native). */
enum class AuthProvider(val path: String, val label: String) { APPLE("apple", "애플"), GOOGLE("google", "Google") }

/** Where a provider's web flow returns: `dearby://auth/apple?code=<UUID>` or `?error=cancelled|failed` (Google has no redirect since 2026-10-19). */
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

/**
 * A received card link (contract "명함 교환", "명함 QR 코드"): `dearby://cards/<cardId>?activity=<n>&code=<QR code>`, or the same card page as an
 * App Link `https://<web origin>/cards/<cardId>?…` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"); [activityId] is the activity's
 * numeric id or null, [code] the owner's QR code (43 base64url characters) that the first save needs, or null. [web] is the App Link entrance,
 * which opens 공유받은 명함 보기 (or the 받은 명함 detail) instead of saving at once.
 */
data class CardLink(val cardId: String, val activityId: Long?, val code: String? = null, val web: Boolean = false)

/** Exactly `dearby://cards/<UUID>` or `https://<[webOrigin] host>/cards/<UUID>` with an optional numeric `activity` and `code`; anything else is not a card link. */
fun cardLink(link: String, webOrigin: String): CardLink? = runCatching {
    val uri = URI(link)
    if (uri.rawFragment != null) return null
    val web = webLink(uri, webOrigin)
    val id = when {
        uri.scheme == "dearby" && uri.rawAuthority == "cards" -> uri.rawPath.removePrefix("/")
        web -> uri.rawPath.takeIf { it.startsWith("/cards/") }?.removePrefix("/cards/") ?: return null
        else -> return null
    }.takeIf { it.isNotEmpty() && '/' !in it } ?: return null
    val cardId = UUID.fromString(id).toString().takeIf { it.equals(id, ignoreCase = true) } ?: return null
    val query = uri.rawQuery?.split('&').orEmpty().mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    CardLink(cardId, query["activity"]?.toLongOrNull(), query["code"]?.takeIf { QR_CODE.matches(it) }, web)
}.getOrNull()

/**
 * The activity id of an App Link `https://<[webOrigin] host>/activities/<numeric id>` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"),
 * which opens that detail on 발견; a query (messengers add tracking keys) is ignored. Anything else is null.
 */
fun activityAppLink(link: String, webOrigin: String): String? = runCatching {
    val uri = URI(link)
    if (uri.rawFragment != null || !webLink(uri, webOrigin)) return null
    uri.rawPath.removePrefix("/activities/").takeIf { it != uri.rawPath && ACTIVITY_ID.matches(it) }
}.getOrNull()

/** An https link on the web origin's host (the App Links intent filter's host). */
private fun webLink(uri: URI, webOrigin: String) = uri.scheme == "https" && uri.host != null && uri.host.equals(URI(webOrigin).host, ignoreCase = true)

private val ACTIVITY_ID = Regex("^[0-9]{1,18}$")
private val QR_CODE = Regex("^[A-Za-z0-9_-]{43}$")

/** What the 내 명함 QR encodes: the card page with the activity the card is handed out at (when there is one) and the QR [code]. */
fun cardQrUrl(webOrigin: String, cardId: String, activityId: String?, code: String): String =
    "${webOrigin.trimEnd('/')}/cards/$cardId?" + (activityId?.let { "activity=$it&" } ?: "") + "code=$code"

/**
 * `dearby://favorites` (the `오늘 일정 n개` push sends `?view=calendar`, contract "프로그램·활동별 알림 수준"). 즐겨찾기 is the calendar only since
 * 2026-10-26, so the query is not read.
 */
fun isFavoritesLink(link: String): Boolean = runCatching {
    val uri = URI(link)
    uri.scheme == "dearby" && uri.rawAuthority == "favorites" && uri.rawFragment == null && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
}.getOrDefault(false)
