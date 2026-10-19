package io.wid.dearby.auth.security

import org.springframework.core.convert.converter.Converter
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
        // aud는 허용 목록(웹·iOS 클라이언트 id) 중 하나면 된다
        fun of(decoder: NimbusJwtDecoder, clientIds: Collection<String>): GoogleTokenDecoder {
            // 기본 변환은 iss를 URL로 바꾸는데 구글의 두 번째 표기(accounts.google.com)는 URL이 아니다. 문자열로 두고 아래에서 비교한다
            decoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(mapOf(JwtClaimNames.ISS to Converter<Any, Any> { it })))
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    { jwt: Jwt ->
                        if (jwt.getClaimAsString(JwtClaimNames.ISS) in ISSUERS) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "iss", null))
                    },
                    { jwt: Jwt ->
                        if (jwt.audience.orEmpty().any { it in clientIds }) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "aud", null))
                    },
                )
            )
            return GoogleTokenDecoder(decoder::decode)
        }
    }
}
