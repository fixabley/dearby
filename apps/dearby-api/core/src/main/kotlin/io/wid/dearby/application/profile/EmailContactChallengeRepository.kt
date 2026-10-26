package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime
import java.util.*

// 이메일 인증번호(native-v1.md "연락처 인증"). 쓴 뒤에도 하루 동안 남겨 같은 이메일의 발송 횟수를 센다
interface EmailContactChallengeRepository {

    // 하루 지난 행은 함께 지운다
    fun create(challenge: EmailContactChallenge)

    // 그 이메일(대소문자 무시)로 since 이후 만든 수. 쓴 것도 센다
    fun countSince(email: String, since: OffsetDateTime): Int

    // 아직 쓰지 않았고 만료되지 않은 것만
    fun find(id: UUID): EmailContactChallenge?

    // 틀린 시도를 하나 더하고 누적 수를 돌려준다
    fun fail(id: UUID): Int

    // 성공했거나 시도 횟수를 넘어 폐기할 때
    fun consume(id: UUID, now: OffsetDateTime)
}

data class EmailContactChallenge(
    val id: UUID,
    val userId: UserId,
    val email: String,
    val codeHash: String,
    val createdAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
)
