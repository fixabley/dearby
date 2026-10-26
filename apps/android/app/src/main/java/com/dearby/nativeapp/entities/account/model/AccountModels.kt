package com.dearby.nativeapp.entities.account.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * TokenPair from the passkey and refresh endpoints: only the two tokens. Stored only through the Keystore-backed vault.
 * The user id and expiry live in the access token's claims; an expired access token is renewed when the API answers 401.
 */
data class AccountSession(val accessToken: String, val refreshToken: String)
/** `{challengeId, options}` from a passkey options call; [options] is the WebAuthn JSON handed to the OS as is. */
data class PasskeyChallenge(val id: String, val options: String)

/** Web sign-in exchange result (Apple, Google): 200 TokenPair, or 202 for a provider account no Dearby account has yet.
 *  The single-use [NeedsAccount.linkToken] (10 minutes) then registers a new account or links a passkey account. */
sealed interface WebSignIn {
    data class SignedIn(val session: AccountSession) : WebSignIn
    data class NeedsAccount(val linkToken: String, val email: String) : WebSignIn
}

/** Profile contact. IDs are client-generated UUIDs; the server keeps them as given. */
data class AccountContact(val id: String, val kind: String, val label: String, val value: String)
/** A registered 활동 정보 item (organization, program, activity or role) a history points at; ids are the server's numbers. */
data class ActivityInfoRef(val id: Long, val name: String)
/** An uploaded evidence file's metadata (contract "인증자료"); the bytes stay on the server. */
data class EvidenceMeta(val id: String, val fileName: String, val contentType: String, val size: Int, val uploadedAt: String)
/**
 * A step-by-step history (contract "이력"): each of organization/program/activity/role is a ref or a typed name.
 * Typed names wait for admin approval ([approval]); evidence drives [verification]. [toJson] writes the PUT shape.
 */
data class AccountHistory(
    val id: String, val kind: String,
    val organization: ActivityInfoRef? = null, val organizationName: String? = null,
    val program: ActivityInfoRef? = null, val programName: String? = null,
    val activity: ActivityInfoRef? = null, val activityName: String? = null,
    val role: ActivityInfoRef? = null, val roleName: String? = null,
    val startDate: String, val endDate: String?, val description: String = "",
    val approval: String = "approved", val approvalReason: String? = null,
    val verification: String = "unverified", val evidence: List<EvidenceMeta> = emptyList(),
) {
    /** 조직 → 프로그램 → 활동 names, registered or typed. */
    val chain get() = listOfNotNull(organization?.name ?: organizationName, program?.name ?: programName, activity?.name ?: activityName)
    val roleTitle get() = role?.name ?: roleName.orEmpty()
    val isApproved get() = approval == "approved"
    val isVerified get() = verification == "verified"
    val hasCustomEntry get() = (organization == null && organizationName != null) || (program == null && programName != null) ||
        (activity == null && activityName != null) || (role == null && roleName != null)
}
/** 명함 머리의 소속(contract "받은 명함 목록 표시"): 승인된 이력 중 진행 중인 것, 그다음 시작일이 늦은 것의 조직 이름. */
fun List<AccountHistory>.currentOrganization(): String? = filter { it.isApproved }.sortedWith(compareBy<AccountHistory> { it.endDate != null }.thenByDescending { it.startDate })
    .firstNotNullOfOrNull { it.organization?.name ?: it.organizationName }?.takeIf { it.isNotBlank() }
/** A 직무 tag (contract "직무"): picked from the job tree ([job]) or free-typed ([jobName] only, approval pending until an admin accepts it). */
data class ProfileJob(val id: String, val job: ActivityInfoRef?, val jobName: String?, val approval: String = "approved", val approvalReason: String? = null) {
    val title get() = job?.name ?: jobName.orEmpty()
    val isCustom get() = job == null
    val isApproved get() = approval == "approved"
}
/**
 * `PUT /v1/profile` replaces the whole profile (data only; card decoration lives on cards). [version] goes back as `If-Match`.
 * [job] is the server's read-only join of approved job names (what cards show); [jobs] is what the profile edits.
 */
/** [introLink] is the 소개 링크 next to the name (contract "소개 링크·소개 글 폐지", 2026-10-26), or null. */
data class AccountProfile(val name: String, val job: String, val introLink: String?, val contacts: List<AccountContact>,
    val histories: List<AccountHistory>, val version: Int = 0, val jobs: List<ProfileJob> = emptyList(),
    /** 개인정보 동의 기록 (contract "개인정보 동의"); null until the account agreed. Not sent on PUT. */
    val consent: ConsentRecord? = null,
    /** True when the server wants (re-)consent before any profile write; missing in older responses → false. */
    val consentRequired: Boolean = false,
    /** 프로필 사진 (contract "프로필 사진", 2026-10-26): `/v1/profile/photo?v=<ms>` (API path, Bearer) or null. Read-only; its own `PUT`/`DELETE`, never in `PUT /v1/profile`. */
    val photoUrl: String? = null)

/** `GET /v1/profile.consent`: the agreed document versions and when. */
data class ConsentRecord(val privacyVersion: String, val termsVersion: String, val consentedAt: String)
/**
 * A published card snapshot from `POST /v1/cards`; [introLink] is the profile 소개 링크 copied at publish (null when left out).
 */
data class PublishedCard(val id: String, val name: String, val description: String, val profileName: String, val job: String,
    val contacts: List<AccountContact>, val histories: List<AccountHistory>, val createdAt: String, val introLink: String? = null,
    /** The card's own copy of the photo at publish (contract "프로필 사진": `/v1/cards/:id/photo?v=<ms>`), or null — also while the server does not send it. */
    val photoUrl: String? = null)
/** One saved profile version (`GET /v1/profile/versions`), newest first. */
data class ProfileVersionSummary(val version: Int, val createdAt: String)
data class ProfileVersionList(val items: List<ProfileVersionSummary>, val nextCursor: Int?)
data class ProfileVersionDetail(val version: Int, val createdAt: String, val profile: AccountProfile)
/** A search hit for the history pickers (`GET /v1/organizations|programs|roles|activities`). */
/** [organization] comes with programs and activities, [program] with activities (an activity's parents fill a history's 조직·프로그램). */
data class ActivityInfoItem(val id: Long, val name: String, val nameEn: String?, val parentOrganizationId: Long?, val organization: ActivityInfoRef?, val parent: ActivityInfoRef? = null, val program: ActivityInfoRef? = null) {
    val ref get() = ActivityInfoRef(id, name)
}
data class ActivityInfoPage(val items: List<ActivityInfoItem>, val nextCursor: Long?)

/** `GET /v1/me` (contract "설정"): when the account was made and which sign-in methods it has. */
data class AccountSummary(val id: String, val createdAt: String, val passkeys: Int, val apple: Boolean, val google: Boolean)
/** One linked Apple or Google account (`GET /v1/me/identities`); [email] is the provider's (possibly relay) address. */
data class LinkedIdentity(val provider: String, val email: String?, val displayName: String?, val linkedAt: String)
/** One of the account's passkeys (`GET /v1/me/passkeys`); [id] is the credential id, [backedUp] whether the authenticator syncs it. */
data class PasskeyItem(val id: String, val label: String, val createdAt: String, val lastUsedAt: String?, val backedUp: Boolean)

/** `GET /v1/wallet`: the account's saved cards (receipts). */
data class Wallet(val items: List<WalletReceipt>)
/**
 * Where a card was exchanged (contract "명함 교환"); names are null when the activity is unknown or was deleted. [scheduleTitle]·[sessionTitle]·
 * [trackName]·[room] (2026-10-26, server `feat/receipt-session-context`) say which block, session and track the QR was shown in; null until the server sends them.
 */
data class ExchangeContext(val activityId: Long?, val activityName: String?, val programName: String?,
    val scheduleTitle: String? = null, val sessionTitle: String? = null, val trackName: String? = null, val room: String? = null)
/** One received card. [reciprocal]: the owner holds one of mine too, so it is never recalled; otherwise [expiresAt] is the recall time. */
/** A QR code for one card (contract "명함 QR 코드"): many people may use it until it is revoked or [expiresAt] passes. */
data class QrCode(val id: String, val code: String, val expiresAt: String)

data class WalletReceipt(val id: String, val card: PublishedCard, val receivedAt: String, val context: ExchangeContext, val reciprocal: Boolean, val expiresAt: String?)

fun JSONObject.toSession() = AccountSession(getString("accessToken"), getString("refreshToken"))
fun JSONObject.toWebSignIn(): WebSignIn =
    if (has("linkToken")) WebSignIn.NeedsAccount(getString("linkToken"), if (isNull("email")) "" else getString("email")) else WebSignIn.SignedIn(toSession())
fun JSONObject.toPasskeyChallenge() = PasskeyChallenge(getString("challengeId"), getJSONObject("options").toString())

fun AccountProfile.toJson(): JSONObject = JSONObject().put("name", name).put("introLink", introLink ?: JSONObject.NULL)
    .put("jobs", JSONArray(jobs.map { JSONObject().put("id", it.id).put("jobId", it.job?.id ?: JSONObject.NULL).put("jobName", it.job?.let { JSONObject.NULL } ?: it.jobName ?: JSONObject.NULL) }))
    .put("contacts", JSONArray(contacts.map { JSONObject().put("id", it.id).put("kind", it.kind).put("label", it.label).put("value", it.value) }))
    .put("histories", JSONArray(histories.map { it.toJson() }))
/** The PUT shape: ids for picked items, names only for typed ones, `endDate` null when ongoing. */
fun AccountHistory.toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind)
    .put("organizationId", organization?.id ?: JSONObject.NULL).put("organizationName", organization?.let { JSONObject.NULL } ?: organizationName ?: JSONObject.NULL)
    .put("programId", program?.id ?: JSONObject.NULL).put("programName", program?.let { JSONObject.NULL } ?: programName ?: JSONObject.NULL)
    .put("activityId", activity?.id ?: JSONObject.NULL).put("activityName", activity?.let { JSONObject.NULL } ?: activityName ?: JSONObject.NULL)
    .put("roleId", role?.id ?: JSONObject.NULL).put("roleName", role?.let { JSONObject.NULL } ?: roleName ?: JSONObject.NULL)
    .put("startDate", startDate).put("endDate", endDate ?: JSONObject.NULL).put("description", description)

fun JSONObject.toProfile() = AccountProfile(optString("name"), optString("job"), text("introLink"),
    optJSONArray("contacts")?.objects()?.map { it.toContact() }.orEmpty(), optJSONArray("histories")?.objects()?.map { it.toHistory() }.orEmpty(), optInt("version", 0),
    optJSONArray("jobs")?.objects()?.map { it.toProfileJob() }.orEmpty(),
    consent = optJSONObject("consent")?.let { ConsentRecord(it.getString("privacyVersion"), it.getString("termsVersion"), it.getString("consentedAt")) },
    consentRequired = optBoolean("consentRequired", false), photoUrl = text("photoUrl"))
fun JSONObject.toProfileJob() = ProfileJob(getString("id"), optJSONObject("job")?.toRef(), text("jobName"), optString("approval", "approved"), text("approvalReason"))
fun JSONObject.toPublishedCard() = PublishedCard(getString("id"), getString("name"), getString("description"), getString("profileName"),
    getString("job"), getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() },
    getString("createdAt"), text("introLink"), text("photoUrl"))
fun JSONObject.toVersionList() = ProfileVersionList(getJSONArray("items").objects().map { ProfileVersionSummary(it.getInt("version"), it.getString("createdAt")) },
    if (isNull("nextCursor")) null else getInt("nextCursor"))
fun JSONObject.toVersionDetail() = ProfileVersionDetail(getInt("version"), getString("createdAt"), getJSONObject("profile").toProfile())
fun JSONObject.toEvidence() = EvidenceMeta(getString("id"), getString("fileName"), getString("contentType"), getInt("size"), getString("uploadedAt"))
fun JSONObject.toActivityInfoPage() = ActivityInfoPage(getJSONArray("items").objects().map {
    ActivityInfoItem(it.getLong("id"), it.getString("name"), it.text("nameEn"), if (it.isNull("parentOrganizationId")) null else it.getLong("parentOrganizationId"),
        it.optJSONObject("organization")?.toRef(), it.optJSONObject("parent")?.toRef(), it.optJSONObject("program")?.toRef())
}, if (isNull("nextCursor")) null else getLong("nextCursor"))

fun JSONObject.toWallet() = Wallet(getJSONArray("items").objects().map { it.toReceipt() })
/** One person blocked from 받은 명함 (contract "명함 신고·차단"): [name] is the card name when blocked. */
data class BlockedPerson(val id: String, val name: String, val blockedAt: String)

fun JSONObject.toBlockedPerson() = BlockedPerson(getString("id"), getString("name"), getString("blockedAt"))
fun JSONObject.toBlockedPeople(): List<BlockedPerson> = getJSONArray("items").let { items -> (0 until items.length()).map { items.getJSONObject(it).toBlockedPerson() } }

fun JSONObject.toQrCode() = QrCode(getString("id"), getString("code"), getString("expiresAt"))

fun JSONObject.toReceipt(): WalletReceipt {
    val context = optJSONObject("context")
    return WalletReceipt(getString("id"), getJSONObject("card").toPublishedCard(), getString("receivedAt"),
        ExchangeContext(context?.takeUnless { it.isNull("activityId") }?.getLong("activityId"), context?.text("activityName"), context?.text("programName"),
            context?.text("scheduleTitle"), context?.text("sessionTitle"), context?.text("trackName"), context?.text("room")),
        optBoolean("reciprocal", false), text("expiresAt"))
}
fun JSONObject.toCardList() = getJSONArray("items").objects().map { it.toPublishedCard() }
fun JSONObject.toAccountSummary(): AccountSummary {
    val methods = optJSONObject("signInMethods")
    return AccountSummary(get("id").toString(), getString("createdAt"), methods?.optInt("passkeys") ?: 0, methods?.optBoolean("apple") ?: false, methods?.optBoolean("google") ?: false)
}
fun JSONObject.toIdentities() = getJSONArray("items").objects().map { LinkedIdentity(it.getString("provider"), it.text("email"), it.text("displayName"), it.getString("linkedAt")) }
fun JSONObject.toPasskeys() = getJSONArray("items").objects().map { it.toPasskey() }
fun JSONObject.toPasskey() = PasskeyItem(getString("id"), optString("label"), getString("createdAt"), text("lastUsedAt"), optBoolean("backedUp", false))

private fun JSONObject.toRef() = ActivityInfoRef(getLong("id"), getString("name"))
private fun JSONObject.toContact() = AccountContact(getString("id"), getString("kind"), getString("label"), getString("value"))
private fun JSONObject.toHistory() = AccountHistory(getString("id"), getString("kind"),
    optJSONObject("organization")?.toRef(), text("organizationName"), optJSONObject("program")?.toRef(), text("programName"),
    optJSONObject("activity")?.toRef(), text("activityName"), optJSONObject("role")?.toRef(), text("roleName"),
    getString("startDate"), text("endDate"), optString("description"), optString("approval", "approved"), text("approvalReason"),
    optString("verification", "unverified"), optJSONArray("evidence")?.objects()?.map { it.toEvidence() }.orEmpty())
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }

/**
 * One 내 활동 record on the server (contract "내 활동 서버 저장·일정 상태", 2026-10-22): `GET /v1/me/activities` items. Ids are the
 * API's numbers as text. [statuses] are the user's per-schedule choices: [MyScheduleStatus.statusId] is null for a default status.
 * [deleted] when the server says the activity was removed or unpublished ("삭제된 활동").
 */
data class MyActivityRecord(val activityId: String, val statuses: List<MyScheduleStatus>, val deleted: Boolean = false)
data class MyScheduleStatus(val scheduleId: String, val statusId: String?, val kind: String)
fun JSONObject.toMyActivities() = getJSONArray("items").objects().map { it.toMyActivity() }
fun JSONObject.toMyActivity() = MyActivityRecord(get("activityId").toString(),
    optJSONArray("statuses")?.objects()?.map { MyScheduleStatus(it.get("scheduleId").toString(), if (it.isNull("statusId")) null else it.get("statusId").toString(), it.getString("kind")) }.orEmpty(), optBoolean("deleted"))
/** `POST /v1/me/activities/import` body: device records with default-kind statuses only (the contract takes `{scheduleId, kind}`). */
fun List<MyActivityRecord>.toImportJson(): JSONObject = JSONObject().put("items", JSONArray(map { record ->
    JSONObject().put("activityId", record.activityId.toLong())
        .put("statuses", JSONArray(record.statuses.map { JSONObject().put("scheduleId", it.scheduleId.toLong()).put("kind", it.kind) }))
}))
