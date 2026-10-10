package com.dearby.nativeapp.entities.account.api

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.CardShare
import com.dearby.nativeapp.entities.account.model.toCardList
import com.dearby.nativeapp.entities.account.model.toCardShare
import com.dearby.nativeapp.entities.account.model.toReceivedShare
import com.dearby.nativeapp.entities.account.model.toWallet
import com.dearby.nativeapp.entities.account.model.toJson
import com.dearby.nativeapp.entities.account.model.toProfile
import com.dearby.nativeapp.entities.account.model.toPublishedCard
import com.dearby.nativeapp.entities.account.model.toPasskeyChallenge
import com.dearby.nativeapp.entities.account.model.toSession
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** [INVALID_CONTACTS]: required phone or email missing or malformed (422 "Invalid contacts"); [OWN_CARD]: the share is your own
 *  card (422 INVALID_RECIPIENT); [CONFLICT]: a card already holds the most share links (409). */
enum class AccountError { UNAUTHORIZED, INVALID_INPUT, INVALID_CONTACTS, OWN_CARD, NOT_FOUND, CONFLICT, RATE_LIMITED, UNAVAILABLE }
class AccountException(val error: AccountError) : Exception(error.name)

data class HttpRequest(val method: String, val url: String, val token: String?, val body: String?)
data class HttpResponse(val status: Int, val body: String)
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
            request.body?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
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
    /** Rotates the pair: the refresh token sent here stops working. */
    suspend fun refresh(refreshToken: String) = send("POST", "auth/refresh", JSONObject().put("refreshToken", refreshToken)).toSession()
    /** 204 with no body, also when the token is already invalid. */
    suspend fun logout(refreshToken: String) { request("POST", "auth/logout", JSONObject().put("refreshToken", refreshToken), null) }
    suspend fun profile(session: AccountSession) = send("GET", "profile", null, session).toProfile()
    suspend fun saveProfile(profile: AccountProfile, session: AccountSession) = send("PUT", "profile", profile.toJson(), session).toProfile()
    suspend fun publish(name: String, description: String, contactIds: List<String>, historyIds: List<String>, session: AccountSession): PublishedCard =
        send("POST", "cards", JSONObject().put("name", name).put("description", description)
            .put("contactIds", JSONArray(contactIds)).put("historyIds", JSONArray(historyIds)), session).toPublishedCard()
    /** Your non-withdrawn cards in creation order, so the newest is last. */
    suspend fun cards(session: AccountSession) = send("GET", "cards", null, session).toCardList()
    suspend fun share(cardId: String, activityIds: List<String>, session: AccountSession): CardShare =
        send("POST", "cards/$cardId/shares", JSONObject().put("activityIds", JSONArray(activityIds)), session).toCardShare()
    /** Saves a received share to the account's wallet (contract #141); returns `saved` or `alreadySaved`. */
    // An explicit {}: HttpURLConnection otherwise sends a PUT body the API rejects.
    suspend fun saveShare(id: String, session: AccountSession) = send("PUT", "wallet/shares/$id", JSONObject(), session).getString("status")
    suspend fun wallet(session: AccountSession) = send("GET", "wallet", null, session).toWallet()
    /** Public: a share and its card, for links and scanned QR codes. No session. */
    suspend fun publicShare(id: String) = send("GET", "shares/$id", null).toReceivedShare()
    /** Public: a card by ID, for legacy `dearby://card/<UUID>` codes. No session. */
    suspend fun publicCard(id: String) = send("GET", "cards/$id", null).toPublishedCard()

    private fun passkeyBody(challengeId: String, credential: String) = JSONObject().put("challengeId", challengeId).put("credential", JSONObject(credential))

    private suspend fun send(method: String, path: String, body: JSONObject?, session: AccountSession? = null): JSONObject {
        val text = request(method, path, body, session)
        return try { JSONObject(text) } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    private suspend fun request(method: String, path: String, body: JSONObject?, session: AccountSession?): String {
        val response = try { transport.send(HttpRequest(method, "$api/v1/$path", session?.accessToken, body?.toString())) }
            catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
        return when (response.status) {
            in 200..299 -> response.body
            401 -> throw AccountException(AccountError.UNAUTHORIZED)
            404 -> throw AccountException(AccountError.NOT_FOUND)
            409 -> throw AccountException(AccountError.CONFLICT)
            422 -> throw AccountException(
                runCatching { JSONObject(response.body).getJSONObject("error") }.getOrNull().let { error ->
                    when {
                        error?.optString("code") == "INVALID_RECIPIENT" -> AccountError.OWN_CARD
                        error?.optString("message") == "Invalid contacts" -> AccountError.INVALID_CONTACTS
                        else -> AccountError.INVALID_INPUT
                    }
                })
            429 -> throw AccountException(AccountError.RATE_LIMITED)
            else -> throw AccountException(AccountError.UNAVAILABLE)
        }
    }
}
