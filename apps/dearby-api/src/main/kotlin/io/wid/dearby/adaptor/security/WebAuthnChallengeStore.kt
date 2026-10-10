package io.wid.dearby.adaptor.security

import io.wid.dearby.adaptor.persistence.jooq.tables.references.WEBAUTHN_CHALLENGES
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

enum class Ceremony { REGISTRATION, AUTHENTICATION }

// 가입 challenge는 새 계정 정보를 함께 가진다. 로그인 challenge는 challenge만 가진다
data class StoredChallenge(
    val challenge: ByteArray,
    val userId: UserId?,
    val userEntityId: String?,
    val displayName: String?,
)

@Component
class WebAuthnChallengeStore(private val dsl: DSLContext) {

    fun issue(ceremony: Ceremony, challenge: StoredChallenge): UUID {
        val id = UUID.randomUUID()
        val now = now()
        // 완료되지 않고 만료된 challenge를 함께 정리해 표가 계속 커지지 않게 한다
        dsl.deleteFrom(WEBAUTHN_CHALLENGES).where(WEBAUTHN_CHALLENGES.EXPIRES_AT.le(now)).execute()
        dsl.insertInto(WEBAUTHN_CHALLENGES)
            .set(WEBAUTHN_CHALLENGES.ID, id)
            .set(WEBAUTHN_CHALLENGES.CEREMONY, ceremony.name)
            .set(WEBAUTHN_CHALLENGES.CHALLENGE, challenge.challenge)
            .set(WEBAUTHN_CHALLENGES.USER_ID, challenge.userId)
            .set(WEBAUTHN_CHALLENGES.USER_ENTITY_ID, challenge.userEntityId)
            .set(WEBAUTHN_CHALLENGES.DISPLAY_NAME, challenge.displayName)
            .set(WEBAUTHN_CHALLENGES.EXPIRES_AT, now.plus(TTL))
            .execute()
        return id
    }

    // 유효한 challenge면 지우고 돌려준다. 조건부 delete라 같은 challenge로 동시에 요청해도 한 번만 성공
    fun consume(id: UUID, ceremony: Ceremony): StoredChallenge? =
        dsl.deleteFrom(WEBAUTHN_CHALLENGES)
            .where(
                WEBAUTHN_CHALLENGES.ID.eq(id),
                WEBAUTHN_CHALLENGES.CEREMONY.eq(ceremony.name),
                WEBAUTHN_CHALLENGES.EXPIRES_AT.gt(now()),
            )
            .returning()
            .fetchOne()
            ?.let { StoredChallenge(it.challenge!!, it.userId, it.userEntityId, it.displayName) }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    companion object {
        val TTL: Duration = Duration.ofMinutes(5)
    }
}
