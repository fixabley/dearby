package io.wid.dearby.adaptor.security

import io.wid.dearby.application.auth.RefreshTokenRepository
import io.wid.dearby.application.auth.TokenPair
import io.wid.dearby.application.auth.TokenService
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import org.springframework.stereotype.Service

@Service
class JwtTokenService(
    private val jwtIssuer: JwtIssuer,
    private val refreshTokens: RefreshTokenRepository,
    private val properties: JwtProperties,
) : TokenService {

    override fun issue(user: User) = TokenPair(
        accessToken = jwtIssuer.issueAccessToken(user),
        refreshToken = refreshTokens.issue(user.id, properties.refreshTokenTtl),
    )

    override fun consumeRefreshToken(refreshToken: String): UserId? = refreshTokens.consume(refreshToken)
}
