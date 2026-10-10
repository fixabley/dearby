package io.wid.dearby.application

import io.wid.dearby.domain.UserId
import java.time.Duration
import java.util.*

enum class Ceremony { REGISTRATION, AUTHENTICATION }

// 가입 challenge는 새 계정 정보를 함께 가진다. 로그인 challenge는 challenge만 가진다
data class StoredChallenge(
    val challenge: ByteArray,
    val userId: UserId?,
    val userEntityId: String?,
    val displayName: String?,
)

// 한 번만 쓸 수 있는 패스키 challenge 저장. 구현은 adaptor/persistence
interface WebAuthnChallengeRepository {

    fun issue(ceremony: Ceremony, challenge: StoredChallenge): UUID

    // 유효한 challenge면 지우고 돌려준다. 같은 challenge로 동시에 요청해도 한 번만 성공
    fun consume(id: UUID, ceremony: Ceremony): StoredChallenge?

    companion object {
        val TTL: Duration = Duration.ofMinutes(5)
    }
}
