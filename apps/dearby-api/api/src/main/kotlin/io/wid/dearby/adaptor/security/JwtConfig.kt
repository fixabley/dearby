package io.wid.dearby.adaptor.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.*

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig {

    @Bean
    fun rsaKey(properties: JwtProperties): RSAKey = loadOrCreateRsaKey(properties.keyPath)

    @Bean
    fun jwtEncoder(rsaKey: RSAKey): JwtEncoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))

    @Bean
    fun jwtDecoder(rsaKey: RSAKey, properties: JwtProperties): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer))
        }
}
