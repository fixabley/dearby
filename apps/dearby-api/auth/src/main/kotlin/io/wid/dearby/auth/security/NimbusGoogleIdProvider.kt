package io.wid.dearby.auth.security

import io.wid.dearby.application.auth.AppleIdentity
import io.wid.dearby.application.auth.GoogleIdProvider
import io.wid.dearby.domain.AuthenticationFailedException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class NimbusGoogleIdProvider(
    private val decoder: GoogleTokenDecoder,
    private val properties: GoogleProperties,
) : GoogleIdProvider {

    // nonce는 앱이 SDK 요청에 넣은 값(서버가 발급)이 ID 토큰의 nonce 클레임으로 그대로 돌아온다(애플과 달리 해시하지 않는다)
    override fun verify(idToken: String, nonce: String): AppleIdentity {
        val jwt = try {
            decoder.decode(idToken)
        } catch (e: JwtException) {
            throw AuthenticationFailedException("구글 로그인을 확인하지 못했습니다", "Google token: ${e.message}")
        }
        val claimed = jwt.getClaimAsString("nonce")
        if (claimed == null || !MessageDigest.isEqual(claimed.toByteArray(), nonce.toByteArray())) {
            throw AuthenticationFailedException("구글 로그인을 확인하지 못했습니다", "Google token nonce mismatch")
        }
        val subject = jwt.subject ?: throw AuthenticationFailedException("구글 로그인을 확인하지 못했습니다", "Google token without sub")
        // 인증되지 않은 이메일은 저장하지 않는다. 이름은 프로필을 채울 때 쓸 수 있게 남긴다
        val email = jwt.getClaimAsString("email")?.takeIf { it.isNotBlank() && jwt.getClaimAsBoolean("email_verified") == true }
        val name = jwt.getClaimAsString("name")?.trim()?.take(MAX_DISPLAY_NAME)?.ifEmpty { null }
        return AppleIdentity(subject, email, name)
    }

    override fun isConfigured(): Boolean = properties.isConfigured()

    private companion object {
        const val MAX_DISPLAY_NAME = 200
    }
}
