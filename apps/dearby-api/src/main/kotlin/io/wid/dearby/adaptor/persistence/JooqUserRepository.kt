package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.USERS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_ROLES
import io.wid.dearby.application.UserRepository
import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.UserRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

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

    override fun create(user: User) {
        dsl.insertInto(USERS).set(USERS.ID, user.id).execute()
        user.roles.forEach {
            dsl.insertInto(USER_ROLES).set(USER_ROLES.USER_ID, user.id).set(USER_ROLES.ROLE, it.name).execute()
        }
    }
}
