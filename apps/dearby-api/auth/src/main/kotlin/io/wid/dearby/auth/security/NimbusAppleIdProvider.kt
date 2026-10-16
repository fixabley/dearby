package io.wid.dearby.auth.security

import io.wid.dearby.application.auth.AppleIdProvider
import io.wid.dearby.application.auth.AppleIdentity
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.ServiceUnavailableException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.security.MessageDigest
import java.util.*

@Component
class NimbusAppleIdProvider(
    private val decoder: AppleTokenDecoder,
    private val properties: AppleProperties,
) : AppleIdProvider {

    // nonce는 클라이언트가 인가 요청에 받은 값을 그대로 넣는다(서버가 발급했으므로 해시하지 않는다)
    override fun verify(identityToken: String, nonce: String): AppleIdentity {
        val jwt = try {
            decoder.decode(identityToken)
        } catch (e: JwtException) {
            throw AuthenticationFailedException("애플 로그인을 확인하지 못했습니다", "Apple token: ${e.message}")
        }
        val claimed = jwt.getClaimAsString("nonce")
        if (claimed == null || !MessageDigest.isEqual(claimed.toByteArray(), nonce.toByteArray())) {
            throw AuthenticationFailedException("애플 로그인을 확인하지 못했습니다", "Apple token nonce mismatch")
        }
        val subject = jwt.subject ?: throw AuthenticationFailedException("애플 로그인을 확인하지 못했습니다", "Apple token without sub")
        // 토큰을 받은 앱(번들 ID 또는 Services ID). 애플 토큰을 받고 폐기할 때 같은 client_id를 쓴다
        val clientId = jwt.audience.orEmpty().firstOrNull { it in properties.clientIds }
        return AppleIdentity(subject, jwt.getClaimAsString("email")?.takeIf { it.isNotBlank() }, null, clientId)
    }

    override fun isConfigured(): Boolean = properties.clientIds.any { it.isNotBlank() }

    override fun authorizeUrl(state: UUID, nonce: String): String {
        val servicesId = properties.servicesId?.takeIf { it.isNotBlank() } ?: throw ServiceUnavailableException("애플 로그인을 지금 쓸 수 없습니다", "dearby.apple.services-id missing")
        val callbackUrl = properties.callbackUrl?.takeIf { it.isNotBlank() } ?: throw ServiceUnavailableException("애플 로그인을 지금 쓸 수 없습니다", "dearby.apple.callback-url missing")
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("client_id", servicesId)
            .queryParam("redirect_uri", callbackUrl)
            .queryParam("response_type", "code id_token")
            // 이름·이메일을 요청하면 애플은 form_post만 허용한다
            .queryParam("response_mode", "form_post")
            .queryParam("scope", "name email")
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            .encode().build().toUriString()
    }

    override fun appRedirect(name: String, value: String): URI =
        UriComponentsBuilder.fromUriString(properties.appRedirectUri).queryParam(name, value).build().toUri()

    private companion object {
        const val AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize"
    }
}
