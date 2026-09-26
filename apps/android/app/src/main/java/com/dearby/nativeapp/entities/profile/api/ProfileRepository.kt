package com.dearby.nativeapp.entities.profile.api

import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.api.wireJson
import com.dearby.nativeapp.shared.storage.DearbyDao
import com.dearby.nativeapp.shared.storage.DocumentRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import com.dearby.nativeapp.entities.profile.model.ProfileModel
import com.dearby.nativeapp.entities.profile.model.update

class ProfileRepository(private val http: HttpClient, private val dao: DearbyDao) {
    var accountId: String? = null
    private fun profileKey() = "account:${requireNotNull(accountId)}:profile"
    suspend fun localProfile(account: Boolean): ProfileModel = dao.document(if (account) profileKey() else "draft:profile")?.let { wireJson.decodeFromString(it.json) } ?: ProfileModel()
    suspend fun saveDraft(profile: ProfileModel) { dao.put(DocumentRecord("draft:profile", wireJson.encodeToString(profile))) }
    suspend fun profile(): ProfileModel = wireJson.decodeFromString<ProfileModel>(http.request("GET", "/profile")).also { dao.put(DocumentRecord(profileKey(), wireJson.encodeToString(it))) }
    suspend fun saveProfile(profile: ProfileModel): ProfileModel = wireJson.decodeFromString<ProfileModel>(http.request("PUT", "/profile", wireJson.encodeToString(profile.update()))).also { dao.put(DocumentRecord(profileKey(), wireJson.encodeToString(it))) }
    suspend fun clearAccount() = dao.clearAccount()
}
