package io.wid.dearby.adaptor.security

import io.wid.dearby.adaptor.persistence.jooq.tables.references.REFRESH_TOKENS
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Component
class RefreshTokenStore(private val dsl: DSLContext, private val properties: JwtProperties) {

    fun issue(userId: UserId): String {
        val token = UUID.randomUUID().toString()
        val now = now()
        dsl.insertInto(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.TOKEN_HASH, hash(token))
            .set(REFRESH_TOKENS.USER_ID, userId)
            .set(REFRESH_TOKENS.EXPIRES_AT, now.plus(properties.refreshTokenTtl))
            .set(REFRESH_TOKENS.CREATED_AT, now)
            .execute()
        return token
    }

    // 유효한 토큰이면 폐기하고 소유자를 돌려준다. 조건부 update라 같은 토큰으로 동시에 요청해도 한 번만 성공
    fun consume(token: String): UserId? {
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
