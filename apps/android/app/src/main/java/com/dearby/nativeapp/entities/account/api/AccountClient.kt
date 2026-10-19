package com.dearby.nativeapp.entities.account.api

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ActivityInfoPage
import com.dearby.nativeapp.entities.account.model.EvidenceMeta
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.toCardList
import com.dearby.nativeapp.entities.account.model.toReceipt
import com.dearby.nativeapp.entities.account.model.toWallet
import com.dearby.nativeapp.entities.account.model.toJson
import com.dearby.nativeapp.entities.account.model.toProfile
import com.dearby.nativeapp.entities.account.model.toPublishedCard
import com.dearby.nativeapp.entities.account.model.toActivityInfoPage
import com.dearby.nativeapp.entities.account.model.toEvidence
import com.dearby.nativeapp.entities.account.model.toVersionDetail
import com.dearby.nativeapp.entities.account.model.toVersionList
import com.dearby.nativeapp.entities.account.model.toWebSignIn
import com.dearby.nativeapp.shared.config.AuthProvider
import com.dearby.nativeapp.entities.account.model.toAccountSummary
import com.dearby.nativeapp.entities.account.model.toIdentities
import com.dearby.nativeapp.entities.account.model.toPasskey
import com.dearby.nativeapp.entities.account.model.toPasskeyChallenge
import com.dearby.nativeapp.entities.account.model.toPasskeys
import com.dearby.nativeapp.entities.account.model.toSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** [BAD_REQUEST]: 400 with the server's user-facing ProblemDetail `detail` in [AccountException.detail] (contact rules,
 *  unapproved history, …); [CONFLICT]: 409 — the profile changed elsewhere (`If-Match`); [TURNED_OFF]: 503, e.g. Apple or Google sign-in not configured on the server.
 *  Network failures and other statuses are [UNAVAILABLE]. */
enum class AccountError { UNAUTHORIZED, FORBIDDEN, INVALID_INPUT, NOT_FOUND, CONFLICT, RATE_LIMITED, BAD_REQUEST, TURNED_OFF, UNAVAILABLE }
/** [detail] is the ProblemDetail sentence; [code] its machine code (e.g. `CONSENT_REQUIRED` on 403, contract "개인정보 동의"; `LAST_SIGN_IN_METHOD` on 409, contract "설정"). */
class AccountException(val error: AccountError, val detail: String? = null, val code: String? = null) : Exception(error.name)

/** [bytes] with [contentType] replaces the JSON [body] (multipart uploads); [headers] are extra request headers. */
data class HttpRequest(val method: String, val url: String, val token: String?, val body: String?, val bytes: ByteArray? = null,
    val contentType: String? = null, val headers: Map<String, String> = emptyMap())
data class HttpResponse(val status: Int, val body: String, val bytes: ByteArray? = null)
fun interface Transport { suspend fun send(request: HttpRequest): HttpResponse }

/**
 * Real transport: OkHttp (already on the classpath through Coil), because Android's `HttpURLConnection` refuses `PATCH`, which
 * `PATCH /v1/me/passkeys/{id}` needs (contract "설정"). 15s timeouts, no redirects, no cache. Tokens, credentials and bodies are never logged.
 */
val httpTransport: Transport = object : Transport {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val body = when {
            request.bytes != null -> request.bytes.toRequestBody((request.contentType ?: "application/octet-stream").toMediaType())
            request.body != null -> request.body.toRequestBody("application/json".toMediaType())
            // OkHttp requires a body for POST/PUT/PATCH; an empty one keeps the contract's bodiless calls working.
            request.method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val built = Request.Builder().url(request.url).method(request.method, body).header("Accept", "application/json").apply {
            request.token?.let { header("Authorization", "Bearer $it") }
            request.headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(built).execute().use { response ->
            val bytes = response.body?.bytes()
            HttpResponse(response.code, bytes?.toString(Charsets.UTF_8).orEmpty(), bytes)
        }
    }
}

/** Owner API (contract native-v1): passkey sign-in, profile and card publishing. */
class AccountClient(private val api: String, private val transport: Transport = httpTransport) {
    /** Passkey sign-up; the account is made when [register] succeeds. The server names the passkey (`Dearby 사용자`). */
    suspend fun registrationOptions() = send("POST", "auth/passkeys/registration/options", JSONObject()).toPasskeyChallenge()
    suspend fun register(challengeId: String, credential: String) = send("POST", "auth/passkeys/registration", passkeyBody(challengeId, credential)).toSession()
    // The contract has no request body; an explicit {} keeps HttpURLConnection from sending a bodiless POST.
    suspend fun authenticationOptions() = send("POST", "auth/passkeys/authentication/options", JSONObject()).toPasskeyChallenge()
    suspend fun authenticate(challengeId: String, credential: String) = send("POST", "auth/passkeys/authentication", passkeyBody(challengeId, credential)).toSession()
    /** Google sign-in (contract "구글 로그인", native): a single-use nonce for the ID token. 503 → [AccountError.TURNED_OFF] (no client ids on the server). */
    suspend fun googleNonce() = send("POST", "auth/google/nonce", JSONObject()).getString("nonce")
    /** Google sign-in: the SDK's ID token with the nonce it was issued for; 200 TokenPair or 202 [WebSignIn.NeedsAccount]. */
    suspend fun googleSignIn(idToken: String, nonce: String) =
        send("POST", "auth/google", JSONObject().put("idToken", idToken).put("nonce", nonce)).toWebSignIn()
    /** Web sign-in (contract "애플 로그인"): the provider's page to open for a SHA-256 of the kept verifier. 503 → [AccountError.TURNED_OFF]. */
    suspend fun webStart(provider: AuthProvider, verifierHash: String) =
        send("POST", "auth/${provider.path}/web/start", JSONObject().put("verifierHash", verifierHash)).getString("authorizeUrl")
    /** The one-time code from `dearby://auth/<provider>?code=` and its verifier → signed in, or a first-seen provider account. */
    suspend fun webExchange(provider: AuthProvider, code: String, verifier: String) =
        send("POST", "auth/${provider.path}/web/exchange", JSONObject().put("code", code).put("verifier", verifier)).toWebSignIn()
    /** "새 계정으로 시작" for a first-seen provider account (201). */
    suspend fun webRegister(provider: AuthProvider, linkToken: String) =
        send("POST", "auth/${provider.path}/register", JSONObject().put("linkToken", linkToken)).toSession()
    /** "기존 패스키 계정에 연결": links the provider account to the signed-in account; 204 with no body. */
    suspend fun linkIdentity(provider: AuthProvider, linkToken: String, session: AccountSession) {
        request("POST", "me/identities/${provider.path}", JSONObject().put("linkToken", linkToken), session)
    }
    // 설정 (contract "설정"): the account summary, linked providers and passkeys. A 409 with code `LAST_SIGN_IN_METHOD` refuses to remove the last way in.
    suspend fun me(session: AccountSession) = send("GET", "me", null, session).toAccountSummary()
    suspend fun identities(session: AccountSession) = send("GET", "me/identities", null, session).toIdentities()
    suspend fun unlinkIdentity(provider: AuthProvider, session: AccountSession) { request("DELETE", "me/identities/${provider.path}", null, session) }
    suspend fun passkeys(session: AccountSession) = send("GET", "me/passkeys", null, session).toPasskeys()
    suspend fun renamePasskey(id: String, label: String, session: AccountSession) = send("PATCH", "me/passkeys/$id", JSONObject().put("label", label), session).toPasskey()
    suspend fun deletePasskey(id: String, session: AccountSession) { request("DELETE", "me/passkeys/$id", null, session) }
    /** 패스키 추가: the registration endpoints with a Bearer add a passkey to this account instead of making one (contract "설정"); the body is not read. */
    suspend fun passkeyOptions(session: AccountSession) = send("POST", "auth/passkeys/registration/options", JSONObject(), session).toPasskeyChallenge()
    suspend fun addPasskey(challengeId: String, credential: String, session: AccountSession) { request("POST", "auth/passkeys/registration", passkeyBody(challengeId, credential), session) }
    /** Rotates the pair: the refresh token sent here stops working. */
    suspend fun refresh(refreshToken: String) = send("POST", "auth/refresh", JSONObject().put("refreshToken", refreshToken)).toSession()
    /** 204 with no body, also when the token is already invalid. */
    suspend fun logout(refreshToken: String) { request("POST", "auth/logout", JSONObject().put("refreshToken", refreshToken), null) }
    /** "계정 삭제" (contract "계정 삭제"): removes the account and everything it holds; 204 with no body. Irreversible. */
    suspend fun deleteAccount(session: AccountSession) { request("DELETE", "me", null, session) }
    suspend fun profile(session: AccountSession) = send("GET", "profile", null, session).toProfile()
    /** 개인정보 동의 (contract "개인정보 동의"): `POST /v1/me/consents` with the agreed document versions; 204, or 400 `CONSENT_VERSION` when stale. */
    suspend fun consent(privacyVersion: String, termsVersion: String, session: AccountSession) {
        request("POST", "me/consents", JSONObject().put("privacyVersion", privacyVersion).put("termsVersion", termsVersion), session)
    }
    /** `If-Match` carries the version the edit started from; a 409 means the profile changed elsewhere. */
    suspend fun saveProfile(profile: AccountProfile, ifMatch: Int?, session: AccountSession) =
        send("PUT", "profile", profile.toJson(), session, headers = ifMatch?.let { mapOf("If-Match" to it.toString()) }.orEmpty()).toProfile()
    suspend fun profileVersions(cursor: Int?, session: AccountSession) = send("GET", "profile/versions?size=20" + (cursor?.let { "&cursor=$it" } ?: ""), null, session).toVersionList()
    suspend fun profileVersion(version: Int, session: AccountSession) = send("GET", "profile/versions/$version", null, session).toVersionDetail()
    /** Makes a new version from an old one (the history is never rewritten). */
    suspend fun restoreProfile(version: Int, session: AccountSession) = send("POST", "profile/versions/$version/restore", JSONObject(), session).toProfile()
    /** Multipart upload of one evidence file (max 3 per history, 5MB, image or PDF). */
    suspend fun uploadEvidence(historyId: String, fileName: String, contentType: String, bytes: ByteArray, session: AccountSession): EvidenceMeta {
        val boundary = "dearby-" + java.util.UUID.randomUUID()
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\nContent-Type: $contentType\r\n\r\n"
        val body = head.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        val text = request(HttpRequest("POST", "$api/v1/profile/histories/$historyId/evidence", session.accessToken, null, body, "multipart/form-data; boundary=$boundary"))
        return try { JSONObject(text).toEvidence() } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    suspend fun deleteEvidence(historyId: String, evidenceId: String, session: AccountSession) { request("DELETE", "profile/histories/$historyId/evidence/$evidenceId", null, session) }
    /** Picker searches for the history steps (organizations, programs, roles need a session; activities are public). */
    suspend fun searchActivityInfo(kind: ActivityInfoKind, query: String, parentId: Long?, session: AccountSession?): ActivityInfoPage {
        val params = mutableListOf("q=" + java.net.URLEncoder.encode(query, "UTF-8"), "size=20")
        parentId?.let { params += when (kind) { ActivityInfoKind.PROGRAMS -> "organizationId=$it"; ActivityInfoKind.ACTIVITIES -> "programId=$it"; ActivityInfoKind.ORGANIZATIONS, ActivityInfoKind.JOBS -> "parentId=$it"; ActivityInfoKind.ROLES -> "" } }
        return send("GET", kind.path + "?" + params.filter { it.isNotEmpty() }.joinToString("&"), null, session).toActivityInfoPage()
    }
    /** `POST /v1/cards`: [template] is the built-in markdown the server fills in at publish time. */
    suspend fun publish(name: String, description: String, template: String, contactIds: List<String>, historyIds: List<String>, session: AccountSession): PublishedCard =
        send("POST", "cards", JSONObject().put("name", name).put("description", description).put("template", template)
            .put("contactIds", JSONArray(contactIds)).put("historyIds", JSONArray(historyIds)), session).toPublishedCard()
    /** 이 명함 지우기 (`DELETE /v1/cards/:id`, contract "명함·지갑"): the public link then answers 404; 204 with no body. */
    suspend fun deleteCard(id: String, session: AccountSession) { request("DELETE", "cards/$id", null, session) }
    /** Your non-withdrawn cards in creation order, so the newest is last. */
    suspend fun cards(session: AccountSession) = send("GET", "cards", null, session).toCardList()
    /** The account's received cards (`GET /v1/wallet`). */
    suspend fun wallet(session: AccountSession) = send("GET", "wallet", null, session).toWallet()
    /** Save a card from its QR link (`PUT /v1/wallet/cards/:cardId`, contract "명함 교환"); 409 (CONFLICT) is the account's own card. */
    suspend fun saveReceipt(cardId: String, activityId: Long?, session: AccountSession) =
        send("PUT", "wallet/cards/$cardId", JSONObject().apply { activityId?.let { put("activityId", it) } }, session).toReceipt()
    /** 내 명함도 건네기 (`POST /v1/wallet/cards/:cardId/reciprocate`): the owner of [cardId] receives [myCardId] in the same activity. */
    suspend fun reciprocate(cardId: String, myCardId: String, session: AccountSession) {
        request("POST", "wallet/cards/$cardId/reciprocate", JSONObject().put("myCardId", myCardId), session)
    }

    private fun passkeyBody(challengeId: String, credential: String) = JSONObject().put("challengeId", challengeId).put("credential", JSONObject(credential))

    enum class ActivityInfoKind(val path: String) { ORGANIZATIONS("organizations"), PROGRAMS("programs"), ACTIVITIES("activities"), ROLES("roles"), JOBS("jobs") }

    private suspend fun send(method: String, path: String, body: JSONObject?, session: AccountSession? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        val text = request(method, path, body, session, headers)
        return try { JSONObject(text) } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    private suspend fun request(method: String, path: String, body: JSONObject?, session: AccountSession?, headers: Map<String, String> = emptyMap()): String =
        request(HttpRequest(method, "$api/v1/$path", session?.accessToken, body?.toString(), headers = headers))
    private suspend fun request(http: HttpRequest): String {
        val response = try { transport.send(http) } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
        return when (response.status) {
            in 200..299 -> response.body
            // RFC 9457 ProblemDetail: `detail` is the user-facing sentence.
            400 -> throw AccountException(AccountError.BAD_REQUEST, runCatching { JSONObject(response.body).optString("detail").ifBlank { null } }.getOrNull())
            401 -> throw AccountException(AccountError.UNAUTHORIZED)
            403 -> throw AccountException(AccountError.FORBIDDEN, code = runCatching { JSONObject(response.body).optString("code").ifBlank { null } }.getOrNull())
            404 -> throw AccountException(AccountError.NOT_FOUND)
            409 -> throw AccountException(AccountError.CONFLICT, code = runCatching { JSONObject(response.body).optString("code").ifBlank { null } }.getOrNull())
            429 -> throw AccountException(AccountError.RATE_LIMITED)
            503 -> throw AccountException(AccountError.TURNED_OFF)
            else -> throw AccountException(AccountError.UNAVAILABLE)
        }
    }
}
