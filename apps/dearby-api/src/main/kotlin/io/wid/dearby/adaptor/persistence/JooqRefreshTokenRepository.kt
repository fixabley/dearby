package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.REFRESH_TOKENS
import io.wid.dearby.application.RefreshTokenRepository
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqRefreshTokenRepository(private val dsl: DSLContext) : RefreshTokenRepository {

    override fun issue(userId: UserId, ttl: Duration): String {
        val token = UUID.randomUUID().toString()
        val now = now()
        dsl.insertInto(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.TOKEN_HASH, hash(token))
            .set(REFRESH_TOKENS.USER_ID, userId)
            .set(REFRESH_TOKENS.EXPIRES_AT, now.plus(ttl))
            .set(REFRESH_TOKENS.CREATED_AT, now)
            .execute()
        return token
    }

    // 조건부 update라 같은 토큰으로 동시에 요청해도 한 번만 성공
    override fun consume(token: String): UserId? {
        val now = now()
        return dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED_AT, now)
            .where(
                REFRESH_TOKENS.TOKEN_HASH.eq(hash(token)),
                REFRESH_TOKENS.REVOKED_AT.isNull,
                REFRESH_TOKENS.EXPIRES_AT.gt(now),
            )
            .returningResult(REFRESH_TOKENS.USER_ID)
            .fetchOne(REFRESH_TOKENS.USER_ID)
    }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    private fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
