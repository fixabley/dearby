package com.dearby.nativeapp.entities.account.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.dearby.nativeapp.entities.account.model.AccountSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    fun load(): AccountSession?
    fun save(session: AccountSession)
    fun clear()
}

/**
 * Keystore-held AES-GCM key; only ciphertext sits in private preferences. New file and key names:
 * sessions kept by the app before the 2026-10-03 prototype are neither read nor removed.
 */
class SessionVault(context: Context, name: String = "account.session.v2") : SessionStore {
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
        val (profileId, token) = String(cipher.doFinal(data), Charsets.UTF_8).split("\n", limit = 2)
        return AccountSession(token, profileId)
    }
    override fun save(session: AccountSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal("${session.profileId}\n${session.sessionToken}".toByteArray())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
        check(preferences.edit().putString("ciphertext", value).commit()) { "Session could not be stored" }
    }
    override fun clear() { check(preferences.edit().clear().commit()) }
}
