package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.EMAIL_CONTACT_CHALLENGES
import io.wid.dearby.application.profile.EmailContactChallenge
import io.wid.dearby.application.profile.EmailContactChallengeRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

@Repository
class JooqEmailContactChallengeRepository(private val dsl: DSLContext) : EmailContactChallengeRepository {

    override fun create(challenge: EmailContactChallenge) {
        dsl.deleteFrom(EMAIL_CONTACT_CHALLENGES).where(EMAIL_CONTACT_CHALLENGES.CREATED_AT.le(challenge.createdAt.minus(KEEP))).execute()
        dsl.insertInto(EMAIL_CONTACT_CHALLENGES)
            .set(EMAIL_CONTACT_CHALLENGES.ID, challenge.id)
            .set(EMAIL_CONTACT_CHALLENGES.USER_ID, challenge.userId)
            .set(EMAIL_CONTACT_CHALLENGES.EMAIL, challenge.email)
            .set(EMAIL_CONTACT_CHALLENGES.CODE_HASH, challenge.codeHash)
            .set(EMAIL_CONTACT_CHALLENGES.CREATED_AT, challenge.createdAt)
            .set(EMAIL_CONTACT_CHALLENGES.EXPIRES_AT, challenge.expiresAt)
            .execute()
    }

    override fun countSince(email: String, since: OffsetDateTime): Int =
        dsl.fetchCount(EMAIL_CONTACT_CHALLENGES, DSL.lower(EMAIL_CONTACT_CHALLENGES.EMAIL).eq(email.lowercase()), EMAIL_CONTACT_CHALLENGES.CREATED_AT.gt(since))

    override fun find(id: UUID): EmailContactChallenge? =
        dsl.selectFrom(EMAIL_CONTACT_CHALLENGES)
            .where(EMAIL_CONTACT_CHALLENGES.ID.eq(id), EMAIL_CONTACT_CHALLENGES.CONSUMED_AT.isNull, EMAIL_CONTACT_CHALLENGES.EXPIRES_AT.gt(DSL.currentOffsetDateTime()))
            .fetchOne()
            ?.let { EmailContactChallenge(it.id!!, it.userId!!, it.email!!, it.codeHash!!, it.createdAt!!, it.expiresAt!!) }

    override fun fail(id: UUID): Int =
        dsl.update(EMAIL_CONTACT_CHALLENGES)
            .set(EMAIL_CONTACT_CHALLENGES.ATTEMPTS, EMAIL_CONTACT_CHALLENGES.ATTEMPTS.plus(1))
            .where(EMAIL_CONTACT_CHALLENGES.ID.eq(id))
            .returning(EMAIL_CONTACT_CHALLENGES.ATTEMPTS)
            .fetchOne()?.attempts ?: 0

    override fun consume(id: UUID, now: OffsetDateTime) {
        dsl.update(EMAIL_CONTACT_CHALLENGES).set(EMAIL_CONTACT_CHALLENGES.CONSUMED_AT, now).where(EMAIL_CONTACT_CHALLENGES.ID.eq(id)).execute()
    }

    private companion object {
        // 하루 10회 제한을 세는 기간
        val KEEP: Duration = Duration.ofHours(24)
    }
}
