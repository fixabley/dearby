package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_HISTORIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ROLES
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Role
import io.wid.dearby.domain.profile.RoleOwner
import io.wid.dearby.domain.profile.RoleOwnerKind
import io.wid.dearby.domain.profile.RoleSource
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqRoleRepository(private val dsl: DSLContext) : RoleRepository {

    // 귀속 단위 이름은 세 테이블 중 하나에서(귀속 열은 정확히 하나)
    private val ownerName = DSL.coalesce(ORGANIZATIONS.NAME, PROGRAMS.NAME, ACTIVITIES.NAME).`as`("owner_name")

    override fun findAllById(ids: Collection<Id>): List<Role> {
        if (ids.isEmpty()) return emptyList()
        return select().where(ROLES.ID.`in`(ids)).fetch().map(::toRole)
    }

    override fun search(kind: RoleOwnerKind, ownerId: Id, q: String?, after: Id?, size: Int): List<Role> {
        val conditions = buildList<Condition> {
            add(owner(kind, ownerId))
            q?.takeIf { it.isNotBlank() }?.trim()?.let { add(ROLES.NAME.containsIgnoreCase(it).or(ROLES.NAME_EN.containsIgnoreCase(it))) }
            after?.let { add(DSL.row(ROLES.POSITION, ROLES.ID).gt(dsl.select(ROLES.POSITION, ROLES.ID).from(ROLES).where(ROLES.ID.eq(it)))) }
        }
        return select().where(conditions).orderBy(ROLES.POSITION, ROLES.ID).limit(size).fetch().map(::toRole)
    }

    override fun create(kind: RoleOwnerKind, ownerId: Id, name: String, nameEn: String?, source: RoleSource): Id {
        val now = now()
        return dsl.insertInto(ROLES)
            .set(ROLES.ORGANIZATION_ID, ownerId.takeIf { kind == RoleOwnerKind.ORGANIZATION })
            .set(ROLES.PROGRAM_ID, ownerId.takeIf { kind == RoleOwnerKind.PROGRAM })
            .set(ROLES.ACTIVITY_ID, ownerId.takeIf { kind == RoleOwnerKind.ACTIVITY })
            .set(ROLES.NAME, name)
            .set(ROLES.NAME_EN, nameEn)
            .set(ROLES.SOURCE, source.name)
            .set(ROLES.POSITION, dsl.select(DSL.coalesce(DSL.max(ROLES.POSITION), -1).plus(1)).from(ROLES).where(owner(kind, ownerId)))
            .set(ROLES.CREATED_AT, now)
            .set(ROLES.UPDATED_AT, now)
            .returning(ROLES.ID)
            .fetchOne()!!.id!!
    }

    override fun update(id: Id, name: String, nameEn: String?, position: Int) {
        dsl.update(ROLES)
            .set(ROLES.NAME, name)
            .set(ROLES.NAME_EN, nameEn)
            .set(ROLES.POSITION, position)
            .set(ROLES.SOURCE, RoleSource.CURATED.name)
            .set(ROLES.UPDATED_AT, now())
            .where(ROLES.ID.eq(id))
            .execute()
    }

    override fun existsName(kind: RoleOwnerKind, ownerId: Id, name: String, exceptId: Id?, english: Boolean): Boolean =
        dsl.fetchExists(
            ROLES,
            owner(kind, ownerId),
            withoutSpaces(if (english) ROLES.NAME_EN else ROLES.NAME).eq(withoutSpaces(DSL.`val`(name))),
            exceptId?.let { ROLES.ID.ne(it) } ?: DSL.noCondition(),
        )

    override fun inUse(id: Id): Boolean = dsl.fetchExists(PROFILE_HISTORIES, PROFILE_HISTORIES.ROLE_ID.eq(id))

    override fun delete(id: Id): Boolean = dsl.deleteFrom(ROLES).where(ROLES.ID.eq(id)).execute() > 0

    private fun select() = dsl.select(ROLES.asterisk(), ownerName).from(ROLES)
        .leftJoin(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(ROLES.ORGANIZATION_ID))
        .leftJoin(PROGRAMS).on(PROGRAMS.ID.eq(ROLES.PROGRAM_ID))
        .leftJoin(ACTIVITIES).on(ACTIVITIES.ID.eq(ROLES.ACTIVITY_ID))

    private fun owner(kind: RoleOwnerKind, ownerId: Id): Condition = when (kind) {
        RoleOwnerKind.ORGANIZATION -> ROLES.ORGANIZATION_ID.eq(ownerId)
        RoleOwnerKind.PROGRAM -> ROLES.PROGRAM_ID.eq(ownerId)
        RoleOwnerKind.ACTIVITY -> ROLES.ACTIVITY_ID.eq(ownerId)
    }

    private fun toRole(record: Record): Role {
        val it = record.into(ROLES)
        val owner = when {
            it.organizationId != null -> RoleOwner(RoleOwnerKind.ORGANIZATION, it.organizationId, record.get(ownerName) ?: "")
            it.programId != null -> RoleOwner(RoleOwnerKind.PROGRAM, it.programId, record.get(ownerName) ?: "")
            else -> RoleOwner(RoleOwnerKind.ACTIVITY, it.activityId!!, record.get(ownerName) ?: "")
        }
        return Role(it.id!!, it.name!!, it.nameEn, owner, RoleSource.valueOf(it.source!!), it.position!!, it.createdAt!!, it.updatedAt!!)
    }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
}
