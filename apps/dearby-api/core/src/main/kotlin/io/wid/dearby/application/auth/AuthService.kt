package io.wid.dearby.application.auth

import io.wid.dearby.application.user.UserService
import io.wid.dearby.domain.AuthenticationFailedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(private val users: UserService, private val tokens: TokenService) {

    // 쓴 refresh token은 폐기하고(회전) 사용자를 다시 읽어 최신 역할로 새 쌍을 발급한다
    @Transactional
    fun refresh(refreshToken: String): TokenPair {
        val userId = tokens.consumeRefreshToken(refreshToken) ?: throw AuthenticationFailedException("refresh token이 없거나 만료되었습니다")
        return tokens.issue(users.getById(userId))
    }

    // 이미 무효여도 성공으로 본다
    fun logout(refreshToken: String) {
        tokens.consumeRefreshToken(refreshToken)
    }
}
