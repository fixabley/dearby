package io.wid.dearby.adaptor.security

import io.wid.dearby.domain.user.User
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JwtIssuer(private val encoder: JwtEncoder, private val properties: JwtProperties) {

    // sub = User.id(리소스 서버 principal 이름, 패스키 user_entities.name과 같은 값), roles = User.roles
    fun issueAccessToken(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(user.id.toString())
            .claim(ROLES_CLAIM, user.roles.map { it.name })
            .issuedAt(now)
            .expiresAt(now + properties.accessTokenTtl)
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    companion object {
        const val ROLES_CLAIM = "roles"
    }
}
