package io.wid.dearby.auth.security

import io.wid.dearby.application.auth.AppleIdentity
import io.wid.dearby.application.auth.GoogleIdProvider
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.ServiceUnavailableException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.MessageDigest
import java.util.*

@Component
class NimbusGoogleIdProvider(
    private val decoder: GoogleTokenDecoder,
    private val properties: GoogleProperties,
) : GoogleIdProvider {

    // nonce는 서버가 인가 요청에 넣은 값이 ID 토큰의 nonce 클레임으로 그대로 돌아온다
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

    override fun authorizeUrl(state: UUID, nonce: String): String {
        if (!properties.isConfigured()) throw ServiceUnavailableException("구글 로그인을 지금 쓸 수 없습니다", "dearby.google settings missing")
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("client_id", properties.clientId)
            .queryParam("redirect_uri", properties.callbackUrl)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            // refresh token은 받지 않는다(access_type=offline 없음). 계정 선택 화면은 항상 보인다
            .queryParam("prompt", "select_account")
            .encode().build().toUriString()
    }

    override fun appRedirect(name: String, value: String): URI =
        UriComponentsBuilder.fromUriString(properties.appRedirectUri).queryParam(name, value).build().toUri()

    private companion object {
        const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val MAX_DISPLAY_NAME = 200
    }
}
