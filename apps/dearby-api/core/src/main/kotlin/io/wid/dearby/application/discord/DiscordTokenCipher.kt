package io.wid.dearby.application.discord

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 디스코드 사용자 토큰(액세스·리프레시)을 저장 전에 암호화한다. AES-256-GCM, 저장 형식 = iv(12바이트) || 암호문+태그(16바이트).
// AAD = 사용자 id라 다른 사용자 행으로 옮긴 암호문은 풀리지 않는다. 키는 DISCORD_TOKEN_KEY(base64 32바이트)
@Component
class DiscordTokenCipher(private val properties: DiscordProperties) {

    private val random = SecureRandom()

    fun seal(plain: String, aad: UUID): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad.toString().toByteArray())
        return iv + cipher.doFinal(plain.toByteArray())
    }

    // 키가 바뀌었거나 행이 손상되면 null(호출 쪽은 다시 연결이 필요한 것으로 본다)
    fun unseal(sealed: ByteArray, aad: UUID): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.updateAAD(aad.toString().toByteArray())
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }.getOrNull()

    // 서비스가 properties.isConfigured()를 먼저 확인한다
    private fun key() = SecretKeySpec(checkNotNull(properties.tokenKeyBytes()) { "dearby.discord.token-key missing or not 32 bytes" }, "AES")

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
