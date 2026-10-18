package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.*
import io.wid.dearby.application.user.UserRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.nio.ByteBuffer
import java.util.*

@Repository
class JooqUserRepository(private val dsl: DSLContext) : UserRepository {

    override fun findById(id: UserId): User? {
        if (!dsl.fetchExists(USERS, USERS.ID.eq(id))) return null
        val roles = dsl.select(USER_ROLES.ROLE)
            .from(USER_ROLES)
            .where(USER_ROLES.USER_ID.eq(id))
            .fetchInto(UserRole::class.java)
        return User(id, roles)
    }

    // users를 지우면 user_roles·contacts·이력(+인증자료)·프로필 버전·받은 명함이 FK cascade로 함께 지워진다.
    // 나머지는 논리키라 직접 지운다. 패스키는 user handle(UUID 16바이트) 또는 2026-10-15 이전 가입분의 name(UUID 문자열)으로 찾는다
    @Transactional
    override fun delete(id: UserId) {
        // 내 명함을 받은 다른 사람의 지갑 행부터(논리키), 그다음 명함
        dsl.deleteFrom(RECEIPTS).where(RECEIPTS.CARD_ID.`in`(dsl.select(BUSINESS_CARDS.ID).from(BUSINESS_CARDS).where(BUSINESS_CARDS.USER_ID.eq(id)))).execute()
        dsl.deleteFrom(BUSINESS_CARDS).where(BUSINESS_CARDS.USER_ID.eq(id)).execute()
        dsl.deleteFrom(ACTIVITY_PARTICIPATIONS).where(ACTIVITY_PARTICIPATIONS.USER_ID.eq(id)).execute()
        dsl.deleteFrom(ORGANIZATION_MEMBERS).where(ORGANIZATION_MEMBERS.USER_ID.eq(id)).execute()
        dsl.deleteFrom(PROGRAM_STAFF).where(PROGRAM_STAFF.USER_ID.eq(id)).execute()
        dsl.deleteFrom(PROFILES).where(PROFILES.USER_ID.eq(id)).execute()
        dsl.deleteFrom(REFRESH_TOKENS).where(REFRESH_TOKENS.USER_ID.eq(id)).execute()
        dsl.deleteFrom(USER_IDENTITIES).where(USER_IDENTITIES.USER_ID.eq(id)).execute()
        val handle = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array())
        dsl.deleteFrom(USER_ENTITIES).where(USER_ENTITIES.ID.eq(handle).or(USER_ENTITIES.NAME.eq(id.toString()))).execute()
        dsl.deleteFrom(USERS).where(USERS.ID.eq(id)).execute()
    }

    override fun create(user: User) {
        dsl.insertInto(USERS).set(USERS.ID, user.id).execute()
        user.roles.forEach {
            dsl.insertInto(USER_ROLES).set(USER_ROLES.USER_ID, user.id).set(USER_ROLES.ROLE, it.name).execute()
        }
    }
}
