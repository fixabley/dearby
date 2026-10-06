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
/** A published card snapshot from `POST /v1/cards`. */
data class PublishedCard(val id: String, val name: String, val description: String, val profileName: String, val job: String,
    val contacts: List<AccountContact>, val histories: List<AccountHistory>, val createdAt: String)

fun AccountProfile.toJson(): JSONObject = JSONObject().put("name", name).put("job", job).put("introduction", introduction)
    .put("contacts", JSONArray(contacts.map { JSONObject().put("id", it.id).put("kind", it.kind).put("label", it.label).put("value", it.value) }))
    // The API requires `endDate` to be present, as null when ongoing.
    .put("histories", JSONArray(histories.map { JSONObject().put("id", it.id).put("title", it.title).put("role", it.role)
        .put("startDate", it.startDate).put("endDate", it.endDate ?: JSONObject.NULL).put("description", it.description) }))

fun JSONObject.toProfile() = AccountProfile(getString("name"), getString("job"), getString("introduction"),
    getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() })
fun JSONObject.toPublishedCard() = PublishedCard(getString("id"), getString("name"), getString("description"), getString("profileName"),
    getString("job"), getJSONArray("contacts").objects().map { it.toContact() }, getJSONArray("histories").objects().map { it.toHistory() },
    getString("createdAt"))

private fun JSONObject.toContact() = AccountContact(getString("id"), getString("kind"), getString("label"), getString("value"))
private fun JSONObject.toHistory() = AccountHistory(getString("id"), getString("title"), getString("role"), getString("startDate"),
    if (isNull("endDate")) null else getString("endDate"), getString("description"))
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
