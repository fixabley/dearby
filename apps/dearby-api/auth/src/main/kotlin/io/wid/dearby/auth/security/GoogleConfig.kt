package io.wid.dearby.auth.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
@EnableConfigurationProperties(GoogleProperties::class)
class GoogleConfig {

    // 구글 공개키(JWKS)는 처음 쓸 때 받아 캐시한다. 기동 때 네트워크를 쓰지 않는다
    @Bean
    fun googleTokenDecoder(properties: GoogleProperties): GoogleTokenDecoder =
        GoogleTokenDecoder.of(
            NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build(),
            properties.clientId,
        )
}
