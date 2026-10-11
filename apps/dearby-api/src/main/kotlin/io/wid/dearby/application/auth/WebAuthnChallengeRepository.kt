package io.wid.dearby.application.auth

import java.time.Duration
import java.util.*

// 한 번만 쓸 수 있는 패스키 challenge 저장. 구현은 adaptor/persistence
interface WebAuthnChallengeRepository {

    fun issue(ceremony: Ceremony, challenge: StoredChallenge): UUID

    // 유효한 challenge면 지우고 돌려준다. 같은 challenge로 동시에 요청해도 한 번만 성공
    fun consume(id: UUID, ceremony: Ceremony): StoredChallenge?

    companion object {
        val TTL: Duration = Duration.ofMinutes(5)
    }
}
