package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_HISTORIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ROLES
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Role
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqRoleRepository(private val dsl: DSLContext) : RoleRepository {

    override fun findAllById(ids: Collection<Id>): List<Role> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(ROLES).where(ROLES.ID.`in`(ids)).fetch().map(::toRole)
    }

    override fun search(q: String?, after: Id?, size: Int): List<Role> {
        val conditions = buildList<Condition> {
            q?.takeIf { it.isNotBlank() }?.trim()?.let { add(ROLES.NAME.containsIgnoreCase(it).or(ROLES.NAME_EN.containsIgnoreCase(it))) }
            after?.let { add(ROLES.ID.gt(it)) }
        }
        return dsl.selectFrom(ROLES).where(conditions).orderBy(ROLES.ID).limit(size).fetch().map(::toRole)
    }

    override fun create(name: String, nameEn: String?): Id {
        val now = now()
        return dsl.insertInto(ROLES)
            .set(ROLES.NAME, name)
            .set(ROLES.NAME_EN, nameEn)
            .set(ROLES.CREATED_AT, now)
            .set(ROLES.UPDATED_AT, now)
            .returning(ROLES.ID)
            .fetchOne()!!.id!!
    }

    override fun update(id: Id, name: String, nameEn: String?) {
        dsl.update(ROLES)
            .set(ROLES.NAME, name)
            .set(ROLES.NAME_EN, nameEn)
            .set(ROLES.UPDATED_AT, now())
            .where(ROLES.ID.eq(id))
            .execute()
    }

    override fun existsName(name: String, exceptId: Id?, english: Boolean): Boolean =
        dsl.fetchExists(
            ROLES,
            withoutSpaces(if (english) ROLES.NAME_EN else ROLES.NAME).eq(withoutSpaces(DSL.`val`(name))),
            exceptId?.let { ROLES.ID.ne(it) } ?: DSL.noCondition(),
        )

    override fun inUse(id: Id): Boolean = dsl.fetchExists(PROFILE_HISTORIES, PROFILE_HISTORIES.ROLE_ID.eq(id))

    override fun delete(id: Id): Boolean = dsl.deleteFrom(ROLES).where(ROLES.ID.eq(id)).execute() > 0

    private fun toRole(it: io.wid.dearby.adaptor.persistence.jooq.tables.records.RolesRecord) =
        Role(it.id!!, it.name!!, it.nameEn, it.createdAt!!, it.updatedAt!!)

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
}
