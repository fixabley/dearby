package io.wid.dearby.auth.security

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.*

// 구글 ID 토큰 해독. 서비스 access token용 JwtDecoder·애플용 AppleTokenDecoder와 섞이지 않게 별도 타입으로 둔다
fun interface GoogleTokenDecoder {

    fun decode(token: String): Jwt

    companion object {
        // 구글은 두 표기를 모두 iss로 쓴다
        val ISSUERS = setOf("https://accounts.google.com", "accounts.google.com")

        // 서명 키만 다르게 해 테스트에서도 같은 검증 규칙(iss·aud·exp)을 쓴다
        fun of(decoder: NimbusJwtDecoder, clientId: String?): GoogleTokenDecoder {
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    { jwt: Jwt ->
                        if (jwt.issuer?.toString() in ISSUERS) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "iss", null))
                    },
                    { jwt: Jwt ->
                        if (!clientId.isNullOrBlank() && clientId in jwt.audience.orEmpty()) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "aud", null))
                    },
                )
            )
            return GoogleTokenDecoder(decoder::decode)
        }
    }
}
