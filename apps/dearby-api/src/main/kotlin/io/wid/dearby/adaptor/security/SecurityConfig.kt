package io.wid.dearby.adaptor.security

import io.wid.dearby.application.UserService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import java.util.*

@Configuration
@EnableConfigurationProperties(WebAuthnProperties::class)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        properties: WebAuthnProperties,
        userService: UserService
    ): SecurityFilterChain {
        // 패스키 검증 뒤 user_entities.name(= UserId)으로 사용자를 만든다. users에 없으면 거부.
        // 빈으로 등록하면 전역 비밀번호 인증에도 쓰이므로 이 체인의 공유 객체로만 둔다.
        http.setSharedObject(UserDetailsService::class.java, UserDetailsService { username ->
            val userId = runCatching { UUID.fromString(username) }.getOrNull()
                ?: throw UsernameNotFoundException(username)
            // 없으면 UserNotFoundException. WebAuthnAuthenticationProvider가 BadCredentialsException(401)으로 바꾼다
            val user = userService.getById(userId)
            User.withUsername(user.id.toString()).roles(*user.roles.map { it.name }.toTypedArray()).build()
        })
        http {
            csrf { disable() }
            cors { disable() }
            authorizeHttpRequests {
                authorize("/v1/auth/refresh", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt {
                    jwtAuthenticationConverter = JwtAuthenticationConverter().apply {
                        setJwtGrantedAuthoritiesConverter(JwtGrantedAuthoritiesConverter().apply {
                            setAuthoritiesClaimName(JwtIssuer.ROLES_CLAIM)
                            setAuthorityPrefix("ROLE_")
                        })
                    }
                }
            }
            webAuthn {
                rpId = properties.rpId
                rpName = properties.rpName
                allowedOrigins = properties.allowedOrigins
                disableDefaultRegistrationPage = true
            }
        }
        return http.build()
    }

    @Bean
    fun userCredentialRepository(jdbc: JdbcOperations) = JdbcUserCredentialRepository(jdbc)

    @Bean
    fun publicKeyCredentialUserEntityRepository(jdbc: JdbcOperations) =
        JdbcPublicKeyCredentialUserEntityRepository(jdbc)
}
