package io.wid.dearby.adaptor.security

import io.wid.dearby.application.TokenPair
import io.wid.dearby.application.TokenService
import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service

@Service
class JwtTokenService(private val jwtIssuer: JwtIssuer, private val refreshTokens: RefreshTokenRepository) : TokenService {

    override fun issue(user: User) = TokenPair(
        accessToken = jwtIssuer.issueAccessToken(user),
        refreshToken = refreshTokens.issue(user.id),
    )

    override fun consumeRefreshToken(refreshToken: String): UserId? = refreshTokens.consume(refreshToken)
}
