package com.dearby.nativeapp.entities.account.model

import org.json.JSONArray
import org.json.JSONObject

/** Owner session from `POST /v1/auth/sessions`; stored only through the Keystore-backed vault. */
data class AccountSession(val sessionToken: String, val profileId: String)

/** Profile contact. IDs are client-generated UUIDs; the server keeps them as given. */
data class AccountContact(val id: String, val kind: String, val label: String, val value: String)
data class AccountHistory(val id: String, val title: String, val role: String, val startDate: String, val endDate: String?, val description: String)
/** `PUT /v1/profile` replaces the whole profile; `GET` returns the same fields plus id and updatedAt. */
data class AccountProfile(val name: String, val job: String, val introduction: String, val contacts: List<AccountContact>, val histories: List<AccountHistory>)
/** A published card snapshot from `POST /v1/cards`; [introduction] comes with public reads. */
data class PublishedCard(val id: String, val name: String, val description: String, val profileName: String, val job: String,
    val contacts: List<AccountContact>, val histories: List<AccountHistory>, val createdAt: String, val introduction: String = "")

/** A catalog activity copied into a share when it was made; never follows later catalog edits. */
data class ShareActivity(val id: String, val title: String)
/** A recorded share of one card (`POST /v1/cards/:id/shares`); its ID is the public `/s/<id>` link. */
/** `GET /v1/wallet` (contract #141): saved cards and the shares they came with. */
data class Wallet(val items: List<WalletReceipt>, val shares: List<WalletShare>)
data class WalletReceipt(val id: String, val card: PublishedCard, val receivedAt: String)
data class WalletShare(val receiptId: String, val cardId: String, val shareId: String, val activities: List<ShareActivity>, val savedAt: String)
/** `GET /v1/shares/:id`: a share and the public card it points at. */
data class ReceivedShare(val share: CardShare, val card: PublishedCard)
data class CardShare(val id: String, val cardId: String, val activities: List<ShareActivity>, val createdAt: String)

fun AccountProfile.toJson(): JSONObject = JSONObject().put("name", name).put("job", job).put("introduction", introduction)
    .put("contacts", JSONArray(contacts.map { JSONObject().put("id", it.id).put("kind", it.kind).put("label", it.label).put("value", it.value) }))
    // The API requires `endDate` to be present, as null when ongoing.
    .put("histories", JSONArray(histories.map { JSONObject().put("id", it.id).put("title", it.title).put("role", it.role)
        .put("startDate", it.startDate).put("endDate", it.endDate ?: JSONObject.NULL).put("description", it.description) }))

fun JSONObject.toProfile() = AccountProfile(getString("name"), getString("job"), getString("introduction"),
    getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() })
fun JSONObject.toPublishedCard() = PublishedCard(getString("id"), getString("name"), getString("description"), getString("profileName"),
    getString("job"), getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() },
    getString("createdAt"), optString("introduction"))

fun JSONObject.toReceivedShare() = ReceivedShare(getJSONObject("share").toCardShare(), getJSONObject("card").toPublishedCard())
fun JSONObject.toWallet() = Wallet(
    getJSONArray("items").objects().map { WalletReceipt(it.getString("id"), it.getJSONObject("card").toPublishedCard(), it.getString("receivedAt")) },
    getJSONArray("shares").objects().map { share -> WalletShare(share.getString("receiptId"), share.getString("cardId"), share.getString("shareId"),
        share.getJSONArray("activities").objects().map { ShareActivity(it.getString("id"), it.getString("title")) }, share.getString("savedAt")) })
fun JSONObject.toCardList() = getJSONArray("items").objects().map { it.toPublishedCard() }
fun JSONObject.toCardShare() = CardShare(getString("id"), getString("cardId"),
    getJSONArray("activities").objects().map { ShareActivity(it.getString("id"), it.getString("title")) }, getString("createdAt"))

private fun JSONObject.toContact() = AccountContact(getString("id"), getString("kind"), getString("label"), getString("value"))
private fun JSONObject.toHistory() = AccountHistory(getString("id"), getString("title"), getString("role"), getString("startDate"),
    if (isNull("endDate")) null else getString("endDate"), getString("description"))
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
