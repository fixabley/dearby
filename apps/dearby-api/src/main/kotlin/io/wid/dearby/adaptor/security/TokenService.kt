package io.wid.dearby.adaptor.security

import io.wid.dearby.application.UserService
import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// expiresIn = access token 남은 초
data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long, val userId: UserId)

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
        userId = user.id,
    )

    // 사용한 refresh token은 폐기하고 새 쌍을 발급(rotation). 무효하면 null. 사용자 삭제 시 토큰도 cascade로 지워져 사용자 없음은 발생하지 않음
    @Transactional
    fun refresh(refreshToken: String): TokenPair? {
        val userId = refreshTokens.consume(refreshToken) ?: return null
        return issue(users.getById(userId))
    }

    // refresh token을 폐기한다. 이미 무효여도 성공으로 본다
    fun revoke(refreshToken: String) {
        refreshTokens.consume(refreshToken)
    }
}
