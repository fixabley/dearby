package com.dearby.nativeapp.features.passkey

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException

/**
 * Credential Manager passkey prompts (contract "패스키 로그인", RP ID `wid.io.kr`). [context] must be the
 * Activity showing the prompt. Options and results are WebAuthn JSON passed through unchanged. Each call
 * returns null when the person closes the prompt and otherwise throws Credential Manager's exceptions,
 * e.g. `NoCredentialException` when this device has no Dearby passkey and the `*UnsupportedException`s below Android 9
 * (minSdk is 26; passkeys start at API 28).
 */
class Passkeys(private val context: Context) {
    private val manager = CredentialManager.create(context)

    /** Sign-up: `PublicKeyCredentialCreationOptionsJSON` in, `RegistrationResponseJSON` out. */
    suspend fun create(options: String): String? = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw CreateCredentialUnsupportedException("Passkeys need Android 9")
        (manager.createCredential(context, CreatePublicKeyCredentialRequest(options)) as CreatePublicKeyCredentialResponse).registrationResponseJson
    } catch (e: CreateCredentialCancellationException) { null }

    /** Sign-in: `PublicKeyCredentialRequestOptionsJSON` in, `AuthenticationResponseJSON` out. Without a saved passkey it fails
     *  at once instead of offering to make one, so the sheet can point to "새 패스키로 시작". */
    suspend fun get(options: String): String? = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw GetCredentialUnsupportedException("Passkeys need Android 9")
        val request = GetCredentialRequest.Builder().addCredentialOption(GetPublicKeyCredentialOption(options))
            .setPreferImmediatelyAvailableCredentials(true).build()
        (manager.getCredential(context, request).credential as PublicKeyCredential).authenticationResponseJson
    } catch (e: GetCredentialCancellationException) { null }
}
