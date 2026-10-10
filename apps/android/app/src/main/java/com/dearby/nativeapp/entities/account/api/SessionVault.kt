package com.dearby.nativeapp.entities.account.api

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.toSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

interface SessionStore {
    fun load(): AccountSession?
    fun save(session: AccountSession)
    fun clear()
}

/**
 * Keystore-held AES-GCM key; only ciphertext sits in private preferences. New file and key names for the
 * 2026-10-10 passkey tokens: earlier stores (the prototype's `session`, the email-code `account.session.v2`)
 * are neither read nor removed.
 */
class SessionVault(context: Context, name: String = "account.tokens.v3") : SessionStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val alias = "dearby.$name"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun load(): AccountSession? {
        val stored = preferences.getString("ciphertext", null) ?: return null
        val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return JSONObject(String(cipher.doFinal(data), Charsets.UTF_8)).toSession()
    }
    override fun save(session: AccountSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val plain = JSONObject().put("accessToken", session.accessToken).put("refreshToken", session.refreshToken)
        val sealed = cipher.doFinal(plain.toString().toByteArray())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
        preferences.edit(commit = true) { putString("ciphertext", value) }
        check(preferences.getString("ciphertext", null) == value) { "Session could not be stored" }
    }
    override fun clear() { preferences.edit(commit = true) { clear() } }
}
