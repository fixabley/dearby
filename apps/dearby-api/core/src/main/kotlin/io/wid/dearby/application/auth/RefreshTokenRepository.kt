package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import java.time.Duration

// refresh token 저장. 원문은 저장하지 않고 해시만 둔다. 구현은 adaptor/persistence
interface RefreshTokenRepository {

    // 새 토큰을 만들어 저장하고 원문을 돌려준다
    fun issue(userId: UserId, ttl: Duration): String

    // 유효한 토큰이면 폐기하고 주인을 돌려준다. 같은 토큰으로 동시에 요청해도 한 번만 성공
    fun consume(token: String): UserId?
}
