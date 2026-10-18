package com.dearby.nativeapp.entities.account.api

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ActivityInfoPage
import com.dearby.nativeapp.entities.account.model.EvidenceMeta
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.toCardList
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
import com.dearby.nativeapp.entities.account.model.toPasskeyChallenge
import com.dearby.nativeapp.entities.account.model.toSession
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** [BAD_REQUEST]: 400 with the server's user-facing ProblemDetail `detail` in [AccountException.detail] (contact rules,
 *  unapproved history, …); [CONFLICT]: 409 — the profile changed elsewhere (`If-Match`); [TURNED_OFF]: 503, e.g. Apple or Google sign-in not configured on the server.
 *  Network failures and other statuses are [UNAVAILABLE]. */
enum class AccountError { UNAUTHORIZED, INVALID_INPUT, NOT_FOUND, CONFLICT, RATE_LIMITED, BAD_REQUEST, TURNED_OFF, UNAVAILABLE }
class AccountException(val error: AccountError, val detail: String? = null) : Exception(error.name)

/** [bytes] with [contentType] replaces the JSON [body] (multipart uploads); [headers] are extra request headers. */
data class HttpRequest(val method: String, val url: String, val token: String?, val body: String?, val bytes: ByteArray? = null,
    val contentType: String? = null, val headers: Map<String, String> = emptyMap())
data class HttpResponse(val status: Int, val body: String, val bytes: ByteArray? = null)
fun interface Transport { suspend fun send(request: HttpRequest): HttpResponse }

/** Real transport. Tokens, credentials and bodies are never logged. */
val httpTransport = Transport { request ->
    withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            request.token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            request.bytes?.let { bytes ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", request.contentType)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() }
            HttpResponse(status, bytes?.toString(Charsets.UTF_8).orEmpty(), bytes)
        } finally {
            connection.disconnect()
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
    /** Web sign-in (contract "애플 로그인"·"구글 로그인"): the provider's page to open for a SHA-256 of the kept verifier. 503 → [AccountError.TURNED_OFF]. */
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
    /** Rotates the pair: the refresh token sent here stops working. */
    suspend fun refresh(refreshToken: String) = send("POST", "auth/refresh", JSONObject().put("refreshToken", refreshToken)).toSession()
    /** 204 with no body, also when the token is already invalid. */
    suspend fun logout(refreshToken: String) { request("POST", "auth/logout", JSONObject().put("refreshToken", refreshToken), null) }
    /** "계정 삭제" (contract "계정 삭제"): removes the account and everything it holds; 204 with no body. Irreversible. */
    suspend fun deleteAccount(session: AccountSession) { request("DELETE", "me", null, session) }
    suspend fun profile(session: AccountSession) = send("GET", "profile", null, session).toProfile()
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
            404 -> throw AccountException(AccountError.NOT_FOUND)
            409 -> throw AccountException(AccountError.CONFLICT)
            429 -> throw AccountException(AccountError.RATE_LIMITED)
            503 -> throw AccountException(AccountError.TURNED_OFF)
            else -> throw AccountException(AccountError.UNAVAILABLE)
        }
    }
}
