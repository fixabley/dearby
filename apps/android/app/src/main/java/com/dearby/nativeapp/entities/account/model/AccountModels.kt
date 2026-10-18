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

/** Apple exchange result: 200 TokenPair, or 202 for an Apple account no Dearby account has yet. The single-use
 *  [NeedsAccount.linkToken] (10 minutes) then registers a new account or links a passkey account. */
sealed interface AppleSignIn {
    data class SignedIn(val session: AccountSession) : AppleSignIn
    data class NeedsAccount(val linkToken: String, val email: String) : AppleSignIn
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
data class AccountProfile(val name: String, val job: String, val introduction: String, val contacts: List<AccountContact>,
    val histories: List<AccountHistory>, val version: Int = 0, val jobs: List<ProfileJob> = emptyList())
/**
 * A published card snapshot from `POST /v1/cards`; [introduction] is the filled-in template (markdown body), [template] comes on owner reads.
 * [webTemplate]/[webIntroduction] are the optional web version (contract "웹 버전"); apps always show [introduction].
 */
data class PublishedCard(val id: String, val name: String, val description: String, val profileName: String, val job: String,
    val contacts: List<AccountContact>, val histories: List<AccountHistory>, val createdAt: String, val introduction: String = "", val template: String? = null,
    val webTemplate: String? = null, val webIntroduction: String? = null)
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

/** `GET /v1/wallet`: the account's saved cards (receipts). */
data class Wallet(val items: List<WalletReceipt>)
data class WalletReceipt(val id: String, val card: PublishedCard, val receivedAt: String)

fun JSONObject.toSession() = AccountSession(getString("accessToken"), getString("refreshToken"))
fun JSONObject.toAppleSignIn(): AppleSignIn =
    if (has("linkToken")) AppleSignIn.NeedsAccount(getString("linkToken"), if (isNull("email")) "" else getString("email")) else AppleSignIn.SignedIn(toSession())
fun JSONObject.toPasskeyChallenge() = PasskeyChallenge(getString("challengeId"), getJSONObject("options").toString())

fun AccountProfile.toJson(): JSONObject = JSONObject().put("name", name).put("introduction", introduction)
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

fun JSONObject.toProfile() = AccountProfile(optString("name"), optString("job"), optString("introduction"),
    optJSONArray("contacts")?.objects()?.map { it.toContact() }.orEmpty(), optJSONArray("histories")?.objects()?.map { it.toHistory() }.orEmpty(), optInt("version", 0),
    optJSONArray("jobs")?.objects()?.map { it.toProfileJob() }.orEmpty())
fun JSONObject.toProfileJob() = ProfileJob(getString("id"), optJSONObject("job")?.toRef(), text("jobName"), optString("approval", "approved"), text("approvalReason"))
fun JSONObject.toPublishedCard() = PublishedCard(getString("id"), getString("name"), getString("description"), getString("profileName"),
    getString("job"), getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() },
    getString("createdAt"), optString("introduction"), text("template"), text("webTemplate"), text("webIntroduction"))
fun JSONObject.toVersionList() = ProfileVersionList(getJSONArray("items").objects().map { ProfileVersionSummary(it.getInt("version"), it.getString("createdAt")) },
    if (isNull("nextCursor")) null else getInt("nextCursor"))
fun JSONObject.toVersionDetail() = ProfileVersionDetail(getInt("version"), getString("createdAt"), getJSONObject("profile").toProfile())
fun JSONObject.toEvidence() = EvidenceMeta(getString("id"), getString("fileName"), getString("contentType"), getInt("size"), getString("uploadedAt"))
fun JSONObject.toActivityInfoPage() = ActivityInfoPage(getJSONArray("items").objects().map {
    ActivityInfoItem(it.getLong("id"), it.getString("name"), it.text("nameEn"), if (it.isNull("parentOrganizationId")) null else it.getLong("parentOrganizationId"),
        it.optJSONObject("organization")?.toRef(), it.optJSONObject("parent")?.toRef(), it.optJSONObject("program")?.toRef())
}, if (isNull("nextCursor")) null else getLong("nextCursor"))

fun JSONObject.toWallet() = Wallet(getJSONArray("items").objects().map { WalletReceipt(it.getString("id"), it.getJSONObject("card").toPublishedCard(), it.getString("receivedAt")) })
fun JSONObject.toCardList() = getJSONArray("items").objects().map { it.toPublishedCard() }

private fun JSONObject.toRef() = ActivityInfoRef(getLong("id"), getString("name"))
private fun JSONObject.toContact() = AccountContact(getString("id"), getString("kind"), getString("label"), getString("value"))
private fun JSONObject.toHistory() = AccountHistory(getString("id"), getString("kind"),
    optJSONObject("organization")?.toRef(), text("organizationName"), optJSONObject("program")?.toRef(), text("programName"),
    optJSONObject("activity")?.toRef(), text("activityName"), optJSONObject("role")?.toRef(), text("roleName"),
    getString("startDate"), text("endDate"), optString("description"), optString("approval", "approved"), text("approvalReason"),
    optString("verification", "unverified"), optJSONArray("evidence")?.objects()?.map { it.toEvidence() }.orEmpty())
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
