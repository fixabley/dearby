package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_IDENTITIES
import io.wid.dearby.application.user.UserIdentityRepository
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqUserIdentityRepository(private val dsl: DSLContext) : UserIdentityRepository {

    override fun findUserId(provider: String, subject: String): UserId? =
        dsl.select(USER_IDENTITIES.USER_ID).from(USER_IDENTITIES)
            .where(USER_IDENTITIES.PROVIDER.eq(provider), USER_IDENTITIES.SUBJECT.eq(subject))
            .fetchOne()?.value1()

    override fun existsForUser(provider: String, userId: UserId): Boolean =
        dsl.fetchExists(USER_IDENTITIES, USER_IDENTITIES.PROVIDER.eq(provider), USER_IDENTITIES.USER_ID.eq(userId))

    override fun create(provider: String, subject: String, userId: UserId, email: String?, displayName: String?) {
        dsl.insertInto(USER_IDENTITIES)
            .set(USER_IDENTITIES.PROVIDER, provider)
            .set(USER_IDENTITIES.SUBJECT, subject)
            .set(USER_IDENTITIES.USER_ID, userId)
            .set(USER_IDENTITIES.EMAIL, email)
            .set(USER_IDENTITIES.DISPLAY_NAME, displayName)
            .set(USER_IDENTITIES.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
    }
}
