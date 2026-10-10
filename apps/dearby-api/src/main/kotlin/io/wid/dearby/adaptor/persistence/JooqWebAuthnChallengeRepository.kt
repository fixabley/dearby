package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.WEBAUTHN_CHALLENGES
import io.wid.dearby.application.auth.Ceremony
import io.wid.dearby.application.auth.StoredChallenge
import io.wid.dearby.application.auth.WebAuthnChallengeRepository
import io.wid.dearby.application.auth.WebAuthnChallengeRepository.Companion.TTL
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqWebAuthnChallengeRepository(private val dsl: DSLContext) : WebAuthnChallengeRepository {

    override fun issue(ceremony: Ceremony, challenge: StoredChallenge): UUID {
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

    // 조건부 delete라 같은 challenge로 동시에 요청해도 한 번만 성공
    override fun consume(id: UUID, ceremony: Ceremony): StoredChallenge? =
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
}
