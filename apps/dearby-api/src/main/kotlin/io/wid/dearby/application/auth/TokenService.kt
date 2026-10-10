package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User

// 응답은 토큰만. 사용자 id·역할·만료는 access token의 sub·roles·exp 클레임에 있다
data class TokenPair(val accessToken: String, val refreshToken: String)

// 토큰 발급·소비. 구현(JWT 서명, refresh token 저장)은 adaptor/security
interface TokenService {

    // 새 refresh token을 저장하고 access token을 서명해 함께 돌려준다
    fun issue(user: User): TokenPair

    // refresh token을 폐기하고 주인 id를 돌려준다. 무효·만료·이미 쓴 토큰이면 null(한 번만 성공)
    fun consumeRefreshToken(refreshToken: String): UserId?
}
