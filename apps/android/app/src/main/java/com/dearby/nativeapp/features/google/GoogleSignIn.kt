package com.dearby.nativeapp.features.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Sign in with Google through Credential Manager (contract "구글 로그인 — 2026-10-19 네이티브 SDK로 전환"). [context] must be the
 * Activity showing the account chooser. Returns the ID token, or null when the person closes the chooser; other Credential
 * Manager failures (`NoCredentialException` without a Google account on the device, …) throw as they are.
 */
class GoogleSignIn(private val context: Context) {
    private val manager = CredentialManager.create(context)

    /** [serverClientId] is the web OAuth client the token is issued for (its `aud`); [nonce] comes from `/v1/auth/google/nonce`. */
    suspend fun idToken(serverClientId: String, nonce: String): String? = try {
        val option = GetGoogleIdOption.Builder().setServerClientId(serverClientId).setNonce(nonce).setFilterByAuthorizedAccounts(false).build()
        val credential = manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        else throw IllegalStateException("Not a Google ID token credential: ${credential.type}")
    } catch (e: GetCredentialCancellationException) { null }
}
