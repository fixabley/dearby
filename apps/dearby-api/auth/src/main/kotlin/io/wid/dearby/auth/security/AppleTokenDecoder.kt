package io.wid.dearby.auth.security

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.*

// 애플 identity token 해독. 서비스 access token용 JwtDecoder 빈과 섞이지 않게 별도 타입으로 둔다(같은 타입 빈이 둘이면 리소스 서버 설정이 깨진다)
fun interface AppleTokenDecoder {

    fun decode(token: String): Jwt

    companion object {
        const val ISSUER = "https://appleid.apple.com"

        // 서명 키만 다르게 해 테스트에서도 같은 검증 규칙(iss·aud·exp)을 쓴다
        fun of(decoder: NimbusJwtDecoder, clientIds: List<String>): AppleTokenDecoder {
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    { jwt: Jwt ->
                        if (jwt.audience.orEmpty().any { it in clientIds }) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "aud", null))
                    },
                )
            )
            return AppleTokenDecoder(decoder::decode)
        }
    }
}
