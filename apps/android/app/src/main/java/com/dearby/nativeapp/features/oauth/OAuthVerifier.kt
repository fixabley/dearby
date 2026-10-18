package com.dearby.nativeapp.features.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Web-flow proof shared by Apple and Google sign-in (contract "애플 로그인"·"구글 로그인", Android): only the hash goes to
 * `web/start`, and only this app, holding the verifier, can exchange the code the provider's redirect carries. Plain JVM.
 */
object OAuthVerifier {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes as base64url without padding: 43 characters. */
    fun create(): String = encoder.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    /** base64url (no padding) of SHA-256 over the verifier's UTF-8 bytes. */
    fun hash(verifier: String): String = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))
}
