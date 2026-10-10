package io.wid.dearby.adaptor.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations

@Configuration
@EnableConfigurationProperties(WebAuthnProperties::class)
class SecurityConfig {

    // 패스키 가입·로그인은 AuthController가 받고 JWT를 발급한다. Spring Security의 세션 기반 webAuthn {} 경로는 쓰지 않는다
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val bearerTokens = DefaultBearerTokenResolver()
        http {
            csrf { disable() }
            cors { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "$AUTH_PATH/**", permitAll)
                // 400 등 오류 응답을 만드는 /error 전달이 401로 바뀌지 않게 연다
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                // 인증 경로에는 만료된 access token이 붙어 와도 무시한다. 그렇지 않으면 refresh 요청이 401로 막힌다
                bearerTokenResolver = BearerTokenResolver { request ->
                    if (request.requestURI.startsWith("$AUTH_PATH/")) null else bearerTokens.resolve(request)
                }
                jwt {
                    jwtAuthenticationConverter = JwtAuthenticationConverter().apply {
                        setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter().apply {
                            setAuthoritiesClaimName(JwtIssuer.ROLES_CLAIM)
                            setAuthorityPrefix("ROLE_")
                        })
                    }
                }
            }
        }
        return http.build()
    }

    @Bean
    fun relyingPartyOperations(
        userEntities: PublicKeyCredentialUserEntityRepository,
        userCredentials: UserCredentialRepository,
        properties: WebAuthnProperties,
    ) = Webauthn4JRelyingPartyOperations(
        userEntities,
        userCredentials,
        PublicKeyCredentialRpEntity.builder().id(properties.rpId).name(properties.rpName).build(),
        properties.allowedOrigins,
    )

    @Bean
    fun userCredentialRepository(jdbc: JdbcOperations) = JdbcUserCredentialRepository(jdbc)

    @Bean
    fun publicKeyCredentialUserEntityRepository(jdbc: JdbcOperations) =
        JdbcPublicKeyCredentialUserEntityRepository(jdbc)

    companion object {
        const val AUTH_PATH = "/v1/auth"
    }
}
