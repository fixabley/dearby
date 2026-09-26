package com.dearby.nativeapp.features.account

import com.dearby.nativeapp.shared.api.*
import com.dearby.nativeapp.shared.storage.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
@Serializable data class ChallengeModel(val challengeId: String, val expiresAt: String)
@Serializable data class SessionModel(val sessionToken: String, val profileId: String)
@Serializable private data class EmailRequest(val email: String)
@Serializable private data class CodeRequest(val challengeId: String, val code: String)

class AuthRepository(private val http: HttpClient, private val dao: DearbyDao) {
    suspend fun challenge(email: String): ChallengeModel = wireJson.decodeFromString(http.request("POST", "/auth/challenges", wireJson.encodeToString(EmailRequest(email)), false))
    suspend fun login(challenge: String, code: String): SessionModel = wireJson.decodeFromString(http.request("POST", "/auth/sessions", wireJson.encodeToString(CodeRequest(challenge, code)), false))
    suspend fun logout() { http.request("DELETE", "/auth/session") }
}
