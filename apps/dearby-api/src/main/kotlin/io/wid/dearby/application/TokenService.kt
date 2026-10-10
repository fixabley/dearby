package io.wid.dearby.application

import io.wid.dearby.adaptor.persistence.RefreshTokenStore
import io.wid.dearby.adaptor.security.JwtIssuer
import io.wid.dearby.adaptor.security.JwtProperties
import io.wid.dearby.domain.TokenPair
import io.wid.dearby.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class TokenService(
    private val jwtIssuer: JwtIssuer,
    private val refreshTokens: RefreshTokenStore,
    private val users: UserService,
    private val properties: JwtProperties,
) {

    fun issue(user: User) = TokenPair(
        accessToken = jwtIssuer.issueAccessToken(user),
        refreshToken = refreshTokens.issue(user.id),
        expiresIn = properties.accessTokenTtl.seconds,
    )

    // 사용한 refresh token은 폐기하고 새 쌍을 발급(rotation). 무효하면 null. 사용자 삭제 시 토큰도 cascade로 지워져 사용자 없음은 발생하지 않음
    @Transactional
    fun refresh(refreshToken: String): TokenPair? {
        val userId = refreshTokens.consume(refreshToken) ?: return null
        return issue(users.getById(userId))
    }
}
