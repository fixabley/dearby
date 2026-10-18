package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.APPLE_SIGN_IN_STATES
import io.wid.dearby.application.auth.AppleIdentity
import io.wid.dearby.application.auth.AppleSignInState
import io.wid.dearby.application.auth.AppleSignInStateRepository
import io.wid.dearby.application.auth.AppleSignInStateRepository.Companion.TTL
import io.wid.dearby.application.auth.AppleSignInStep
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqAppleSignInStateRepository(private val dsl: DSLContext) : AppleSignInStateRepository {

    override fun issue(step: AppleSignInStep, state: AppleSignInState): UUID {
        val id = UUID.randomUUID()
        val now = now()
        // 쓰지 않고 만료된 상태를 함께 정리한다
        dsl.deleteFrom(APPLE_SIGN_IN_STATES).where(APPLE_SIGN_IN_STATES.EXPIRES_AT.le(now)).execute()
        dsl.insertInto(APPLE_SIGN_IN_STATES)
            .set(APPLE_SIGN_IN_STATES.ID, id)
            .set(APPLE_SIGN_IN_STATES.STEP, step.name)
            .set(APPLE_SIGN_IN_STATES.PROVIDER, state.provider)
            .set(APPLE_SIGN_IN_STATES.NONCE, state.nonce)
            .set(APPLE_SIGN_IN_STATES.VERIFIER_HASH, state.verifierHash)
            .set(APPLE_SIGN_IN_STATES.SUBJECT, state.identity?.subject)
            .set(APPLE_SIGN_IN_STATES.EMAIL, state.identity?.email)
            .set(APPLE_SIGN_IN_STATES.DISPLAY_NAME, state.identity?.displayName)
            .set(APPLE_SIGN_IN_STATES.CLIENT_ID, state.identity?.clientId)
            .set(APPLE_SIGN_IN_STATES.REFRESH_TOKEN, state.identity?.refreshToken)
            .set(APPLE_SIGN_IN_STATES.EXPIRES_AT, now.plus(TTL))
            .execute()
        return id
    }

    // 조건부 delete라 같은 id로 동시에 요청해도 한 번만 성공
    override fun consume(id: UUID, step: AppleSignInStep, provider: String): AppleSignInState? =
        dsl.deleteFrom(APPLE_SIGN_IN_STATES)
            .where(
                APPLE_SIGN_IN_STATES.ID.eq(id),
                APPLE_SIGN_IN_STATES.STEP.eq(step.name),
                APPLE_SIGN_IN_STATES.PROVIDER.eq(provider),
                APPLE_SIGN_IN_STATES.EXPIRES_AT.gt(now()),
            )
            .returning()
            .fetchOne()
            ?.let {
                AppleSignInState(
                    nonce = it.nonce,
                    verifierHash = it.verifierHash,
                    identity = it.subject?.let { subject -> AppleIdentity(subject, it.email, it.displayName, it.clientId, it.refreshToken) },
                    provider = it.provider!!,
                )
            }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
}
