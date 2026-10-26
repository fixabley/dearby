package com.dearby.nativeapp.entities.account.api

import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ActivityInfoPage
import com.dearby.nativeapp.entities.account.model.EvidenceMeta
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.MyActivityRecord
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.toImportJson
import com.dearby.nativeapp.entities.account.model.toMyActivities
import com.dearby.nativeapp.entities.account.model.toMyActivity
import com.dearby.nativeapp.entities.account.model.toCardList
import com.dearby.nativeapp.entities.account.model.toBlockedPeople
import com.dearby.nativeapp.entities.account.model.toBlockedPerson
import com.dearby.nativeapp.entities.account.model.toQrCode
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
import com.dearby.nativeapp.entities.account.model.EligibilityProfile
import com.dearby.nativeapp.entities.account.model.RecommendationPage
import com.dearby.nativeapp.entities.account.model.RecommendationPreferences
import com.dearby.nativeapp.entities.account.model.toDictionaryTags
import com.dearby.nativeapp.entities.account.model.toEligibilityProfile
import com.dearby.nativeapp.entities.account.model.toPutJson
import com.dearby.nativeapp.entities.account.model.toRecommendationPreferences
import com.dearby.nativeapp.entities.account.model.toRecommendations
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
        val text = upload("POST", "profile/histories/$historyId/evidence", fileName, contentType, bytes, session)
        return try { JSONObject(text).toEvidence() } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    /** 프로필 사진 (contract "프로필 사진"): the square JPEG the crop screen made; replaces any earlier one. Returns the new `photoUrl`. */
    suspend fun uploadProfilePhoto(jpeg: ByteArray, session: AccountSession): String {
        val text = upload("PUT", "profile/photo", "photo.jpg", "image/jpeg", jpeg, session)
        return try { JSONObject(text).getString("photoUrl") } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    /** 204, also when there was none. Cards already made keep their own copy. */
    suspend fun deleteProfilePhoto(session: AccountSession) { request("DELETE", "profile/photo", null, session) }
    /**
     * A photo by its API path (`photoUrl`, e.g. `/v1/profile/photo?v=…`, `/v1/cards/:id/photo?v=…`): the profile one needs the owner's [session];
     * a card's has the card's read permission (owner, holder or `?code=`). Asks for an image, not the JSON the other calls accept.
     */
    suspend fun photo(path: String, session: AccountSession?): ByteArray =
        exchange(HttpRequest("GET", api + path, session?.accessToken, null, headers = mapOf("Accept" to "image/jpeg, image/*"))).bytes ?: throw AccountException(AccountError.UNAVAILABLE)
    suspend fun deleteEvidence(historyId: String, evidenceId: String, session: AccountSession) { request("DELETE", "profile/histories/$historyId/evidence/$evidenceId", null, session) }
    /**
     * Picker searches for the history steps (organizations, programs, roles need a session; activities are public). Activities go with `forHistory=true`
     * (contract "활동 이력에서 단기 행사 제외", 2026-10-20) so events under a week are left out; discovery uses `fetchActivities` and is unaffected.
     */
    suspend fun searchActivityInfo(kind: ActivityInfoKind, query: String, parentId: Long?, session: AccountSession?): ActivityInfoPage {
        val params = mutableListOf("q=" + java.net.URLEncoder.encode(query, "UTF-8"), "size=20")
        if (kind == ActivityInfoKind.ACTIVITIES) params += "forHistory=true"
        parentId?.let { params += when (kind) { ActivityInfoKind.PROGRAMS -> "organizationId=$it"; ActivityInfoKind.ACTIVITIES -> "programId=$it"; ActivityInfoKind.ORGANIZATIONS, ActivityInfoKind.JOBS -> "parentId=$it"; ActivityInfoKind.ROLES -> "" } }
        return send("GET", kind.path + "?" + params.filter { it.isNotEmpty() }.joinToString("&"), null, session).toActivityInfoPage()
    }
    /** `POST /v1/cards`: the server copies the profile introduction as the body (no `template` since 2026-10-26) and, with [includePhoto], the profile photo (contract "프로필 사진"). */
    suspend fun publish(name: String, description: String, contactIds: List<String>, historyIds: List<String>, includePhoto: Boolean, session: AccountSession): PublishedCard =
        send("POST", "cards", cardBody(name, description, contactIds, historyIds, includePhoto), session).toPublishedCard()
    /** 이 명함 고치기 (`PUT /v1/cards/:id`, contract "명함 고치기는 같은 명함을 수정", 2026-10-20): same body and validation as [publish]; the snapshot behind the same id is rebuilt from the current profile (200 Card, `id`·`createdAt` kept). Owner only; a withdrawn card is 404. */
    suspend fun updateCard(id: String, name: String, description: String, contactIds: List<String>, historyIds: List<String>, includePhoto: Boolean, session: AccountSession): PublishedCard =
        send("PUT", "cards/$id", cardBody(name, description, contactIds, historyIds, includePhoto), session).toPublishedCard()
    private fun cardBody(name: String, description: String, contactIds: List<String>, historyIds: List<String>, includePhoto: Boolean) =
        JSONObject().put("name", name).put("description", description).put("contactIds", JSONArray(contactIds)).put("historyIds", JSONArray(historyIds)).put("includePhoto", includePhoto)
    /** 이 명함 지우기 (`DELETE /v1/cards/:id`, contract "명함·지갑"): the public link then answers 404; 204 with no body. */
    suspend fun deleteCard(id: String, session: AccountSession) { request("DELETE", "cards/$id", null, session) }
    /** Your non-withdrawn cards in creation order, so the newest is last. */
    suspend fun cards(session: AccountSession) = send("GET", "cards", null, session).toCardList()
    /**
     * The public card (`GET /v1/cards/:id?code=`, the same read as the web `/cards/:id`; contract "유니버설 링크·App Links·활동 공유·오픈그래프"):
     * 403 (FORBIDDEN) is a missing or expired QR [code], 404 a withdrawn card. The [session], when signed in, lets the owner and holders read it without a code.
     */
    suspend fun publicCard(cardId: String, code: String?, session: AccountSession?) =
        send("GET", "cards/$cardId" + (code?.let { "?code=$it" } ?: ""), null, session).toPublishedCard()
    /** The account's received cards (`GET /v1/wallet`). */
    suspend fun wallet(session: AccountSession) = send("GET", "wallet", null, session).toWallet()
    /**
     * Save a card from its QR link (`PUT /v1/wallet/cards/:cardId`, contract "명함 교환"); 409 (CONFLICT) is the account's own card, 403 (FORBIDDEN) a
     * first save whose QR [code] is missing or expired (contract "명함 QR 코드").
     */
    suspend fun saveReceipt(cardId: String, activityId: Long?, code: String?, session: AccountSession) =
        send("PUT", "wallet/cards/$cardId", JSONObject().apply { activityId?.let { put("activityId", it) }; code?.let { put("code", it) } }, session).toReceipt()
    /** 신고하기 (`POST /v1/cards/:id/reports`, contract "명함 신고·차단"): [reason] is SPAM·IMPERSONATION·INAPPROPRIATE·OTHER. */
    suspend fun reportCard(cardId: String, reason: String, detail: String?, session: AccountSession) {
        request("POST", "cards/$cardId/reports", JSONObject().put("reason", reason).apply { detail?.let { put("detail", it) } }, session)
    }
    /** 삭제하고 차단하기 (`POST /v1/wallet/blocks`): the card's owner leaves 받은 명함 and cannot be received again. */
    suspend fun block(cardId: String, session: AccountSession) = send("POST", "wallet/blocks", JSONObject().put("cardId", cardId), session).toBlockedPerson()
    suspend fun blocks(session: AccountSession) = send("GET", "wallet/blocks", null, session).toBlockedPeople()
    /** 차단 해제: removed cards do not come back. */
    suspend fun unblock(id: String, session: AccountSession) { request("DELETE", "wallet/blocks/$id", null, session) }
    /** QR 보이기 (`POST /v1/cards/:id/qr-codes`, contract "명함 QR 코드"): a new code for the owner's card. */
    suspend fun issueQrCode(cardId: String, session: AccountSession) = send("POST", "cards/$cardId/qr-codes", JSONObject(), session).toQrCode()
    /** The QR screen closed or was refreshed: the code stops working (204 even when it already had). */
    suspend fun revokeQrCode(cardId: String, codeId: String, session: AccountSession) { request("DELETE", "cards/$cardId/qr-codes/$codeId", null, session) }
    /** 내 명함도 건네기 (`POST /v1/wallet/cards/:cardId/reciprocate`): the owner of [cardId] receives [myCardId] in the same activity. */
    suspend fun reciprocate(cardId: String, myCardId: String, session: AccountSession) {
        request("POST", "wallet/cards/$cardId/reciprocate", JSONObject().put("myCardId", myCardId), session)
    }

    // 내 활동 (contract "내 활동 서버 저장·일정 상태", 2026-10-22): the signed-in account's applied activities and stage statuses (참여 확정 was dropped the same day; the state follows from the statuses).
    suspend fun myActivities(session: AccountSession) = send("GET", "me/activities", null, session).toMyActivities()
    /** Adds it (idempotent, empty body); 404 when the activity is not published. */
    suspend fun saveMyActivity(activityId: String, session: AccountSession) = send("PUT", "me/activities/$activityId", JSONObject(), session).toMyActivity()
    /** 204, also when it was not there; the stage statuses go with it. */
    suspend fun deleteMyActivity(activityId: String, session: AccountSession) { request("DELETE", "me/activities/$activityId", null, session) }
    /** One stage's status: an admin status by [statusId], or a default status by [kind] when [statusId] is null; 400 when it is not that schedule's. */
    suspend fun saveScheduleStatus(activityId: String, scheduleId: String, statusId: String?, kind: String, session: AccountSession) =
        send("PUT", "me/activities/$activityId/schedules/$scheduleId", if (statusId != null) JSONObject().put("statusId", statusId.toLong()) else JSONObject().put("kind", kind), session).toMyActivity()
    /** The device-only records, once, right after signing in; records the server already has keep the server's values. */
    suspend fun importMyActivities(records: List<MyActivityRecord>, session: AccountSession) = send("POST", "me/activities/import", records.toImportJson(), session).toMyActivities()

    // 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24). 404 ([AccountError.NOT_FOUND]) means the server does not have it yet; callers hide the feature.
    suspend fun recommendationPreferences(session: AccountSession) = send("GET", "me/recommendation-preferences", null, session).toRecommendationPreferences()
    suspend fun saveRecommendationPreferences(preferences: RecommendationPreferences, session: AccountSession) {
        request("PUT", "me/recommendation-preferences", preferences.toJson(), session)
    }
    /** 지원 자격 정보: null values when nothing was entered. The values are never logged. */
    suspend fun eligibilityProfile(session: AccountSession) = send("GET", "me/eligibility-profile", null, session).toEligibilityProfile()
    /** 400 when [EligibilityProfile.consentVersion] is not the current version. */
    suspend fun saveEligibilityProfile(profile: EligibilityProfile, session: AccountSession) { request("PUT", "me/eligibility-profile", profile.toPutJson(), session) }
    /** Removes the values and the consent record. */
    suspend fun deleteEligibilityProfile(session: AccountSession) { request("DELETE", "me/eligibility-profile", null, session) }
    // 광고 목적 개인정보 제3자 제공 동의(선택) (contract, 2026-10-25): `agreed` is true only for the current version.
    suspend fun adConsent(session: AccountSession) = send("GET", "me/consents/ad-third-party", null, session).getBoolean("agreed")
    /** 400 `CONSENT_VERSION` when [version] is not the current one. */
    suspend fun agreeAdConsent(version: String, session: AccountSession) { request("PUT", "me/consents/ad-third-party", JSONObject().put("version", version), session) }
    /** 204, also when there was none. */
    suspend fun withdrawAdConsent(session: AccountSession) { request("DELETE", "me/consents/ad-third-party", null, session) }
    /** The server uses the saved settings and 지원 자격 정보; signed out is 401 (contract "비로그인에서 만들기·추천을 막음", 2026-10-25). */
    suspend fun recommendations(session: AccountSession, size: Int = 10): RecommendationPage =
        send("GET", "discovery/recommendations?size=$size", null, session).toRecommendations(api)
    /** 오늘의 맞춤 대기열 (contract "맞춤 대기열"): `{date, items: [{activity, program, reasons}], seen}`; 404 before the server ships it. */
    suspend fun discoveryQueue(session: AccountSession) = send("GET", "me/discovery-queue", null, session)
    /** `더 받기`: the queue rebuilt from the remaining candidates, in the same shape. */
    suspend fun refillDiscoveryQueue(session: AccountSession) = send("POST", "me/discovery-queue/refill", JSONObject(), session)
    /** One reaction (`LIKE`·`UNLIKE`·`DISMISS`·`LESS_FIELD` with [tagId]·`HIDE_PROGRAM`·`INELIGIBLE`·`COMPLETE`·`SKIP_EARLY`·`OPEN_DETAIL`); signed in only. */
    suspend fun sendRecommendationSignal(activityId: String, kind: String, tagId: String?, session: AccountSession) {
        request("POST", "me/recommendation-signals", JSONObject().put("activityId", activityId.toLong()).put("kind", kind).apply { tagId?.let { put("tagId", it.toLong()) } }, session)
    }
    // 즐겨찾기·새 모집 푸시 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25). 404 ([AccountError.NOT_FOUND]) before the server ships them.
    /** The raw `{programs, activities}`; `entities/activity/api/parseFavorites` reads it. */
    suspend fun favorites(session: AccountSession) = send("GET", "me/favorites", null, session)
    /** [kind] is `programs` or `activities`. Idempotent 204; [notifyLevel] (`ALL`·`APPLICATION`·`NONE`) null keeps the server default or the saved level. */
    suspend fun saveFavorite(kind: String, id: String, notifyLevel: String?, session: AccountSession) {
        request("PUT", "me/favorites/$kind/$id", notifyLevel?.let { JSONObject().put("notifyLevel", it) } ?: JSONObject(), session)
    }
    suspend fun deleteFavorite(kind: String, id: String, session: AccountSession) { request("DELETE", "me/favorites/$kind/$id", null, session) }
    /** The program was opened from 즐겨찾기: its `새 모집` badge goes. */
    suspend fun seenFavoriteProgram(id: String, session: AccountSession) { request("POST", "me/favorites/programs/$id/seen", JSONObject(), session) }
    /** This device's FCM token with its IANA [timeZone] (the server sends at 8 in the morning there). The token is never logged. */
    suspend fun savePushDevice(token: String, timeZone: String, session: AccountSession) {
        request("PUT", "me/push-devices", JSONObject().put("platform", "ANDROID").put("token", token).put("timeZone", timeZone), session)
    }
    suspend fun deletePushDevice(token: String, session: AccountSession) {
        request("DELETE", "me/push-devices/" + java.net.URLEncoder.encode(token, "UTF-8"), null, session)
    }
    /** `{newRecruitment}`: the account-wide 새 모집 알림 switch. */
    suspend fun newRecruitmentNotifications(session: AccountSession) = send("GET", "me/notification-settings", null, session).optBoolean("newRecruitment", true)
    suspend fun saveNewRecruitmentNotifications(on: Boolean, session: AccountSession) {
        request("PUT", "me/notification-settings", JSONObject().put("newRecruitment", on), session)
    }
    // 알림 목록 (contract "알림 목록(발견 우상단 종)", 2026-10-25); 404 before the server ships it.
    /** The raw `{groups, nextCursor}`; `entities/activity/api/parseInbox` reads it. */
    suspend fun notifications(cursor: String?, session: AccountSession) = send("GET", "me/notifications" + (cursor?.let { "?cursor=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""), null, session)
    suspend fun unreadNotifications(session: AccountSession) = send("GET", "me/notifications/unread-count", null, session).optInt("count", 0)
    suspend fun readNotification(id: String, session: AccountSession) { request("POST", "me/notifications/$id/read", JSONObject(), session) }
    suspend fun readAllNotifications(session: AccountSession) { request("POST", "me/notifications/read-all", JSONObject(), session) }
    /** The public tag dictionary (`GET /v1/tags`) with kinds and upper fields, for the funnel's chips. */
    suspend fun tags() = send("GET", "tags", null).toDictionaryTags()

    private fun passkeyBody(challengeId: String, credential: String) = JSONObject().put("challengeId", challengeId).put("credential", JSONObject(credential))

    enum class ActivityInfoKind(val path: String) { ORGANIZATIONS("organizations"), PROGRAMS("programs"), ACTIVITIES("activities"), ROLES("roles"), JOBS("jobs") }

    private suspend fun send(method: String, path: String, body: JSONObject?, session: AccountSession? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        val text = request(method, path, body, session, headers)
        return try { JSONObject(text) } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
    }
    /** One `file` part (multipart/form-data). */
    private suspend fun upload(method: String, path: String, fileName: String, contentType: String, bytes: ByteArray, session: AccountSession): String {
        val boundary = "dearby-" + java.util.UUID.randomUUID()
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\nContent-Type: $contentType\r\n\r\n"
        val body = head.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        return request(HttpRequest(method, "$api/v1/$path", session.accessToken, null, body, "multipart/form-data; boundary=$boundary"))
    }
    private suspend fun request(method: String, path: String, body: JSONObject?, session: AccountSession?, headers: Map<String, String> = emptyMap()): String =
        request(HttpRequest(method, "$api/v1/$path", session?.accessToken, body?.toString(), headers = headers))
    private suspend fun request(http: HttpRequest): String = exchange(http).body
    private suspend fun exchange(http: HttpRequest): HttpResponse {
        val response = try { transport.send(http) } catch (e: Exception) { throw AccountException(AccountError.UNAVAILABLE) }
        return when (response.status) {
            in 200..299 -> response
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
