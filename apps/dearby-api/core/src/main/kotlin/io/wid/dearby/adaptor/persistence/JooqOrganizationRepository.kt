package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATION_CONTACTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.*
import io.wid.dearby.adaptor.persistence.jooq.tables.records.OrganizationsRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqOrganizationRepository(private val dsl: DSLContext) : OrganizationRepository {

    override fun findAllById(ids: Collection<Id>): List<Organization> {
        if (ids.isEmpty()) return emptyList()
        return load(dsl.selectFrom(ORGANIZATIONS).where(ORGANIZATIONS.ID.`in`(ids)).fetch())
    }

    override fun search(q: String?, parentId: Id?, rootsOnly: Boolean, after: Id?, size: Int): List<Organization> {
        val conditions = buildList<Condition> {
            q?.takeIf { it.isNotBlank() }?.let { add(ORGANIZATIONS.NAME.containsIgnoreCase(it.trim())) }
            parentId?.let { add(ORGANIZATIONS.PARENT_ORGANIZATION_ID.eq(it)) }
            if (rootsOnly) add(ORGANIZATIONS.PARENT_ORGANIZATION_ID.isNull)
            after?.let { add(ORGANIZATIONS.ID.gt(it)) }
        }
        return load(dsl.selectFrom(ORGANIZATIONS).where(conditions).orderBy(ORGANIZATIONS.ID).limit(size).fetch())
    }

    override fun create(name: String, parentOrganizationId: Id?): Id {
        val now = LocalDateTime.now()
        return dsl.insertInto(ORGANIZATIONS)
            .set(ORGANIZATIONS.NAME, name)
            .set(ORGANIZATIONS.PARENT_ORGANIZATION_ID, parentOrganizationId)
            .set(ORGANIZATIONS.CREATED_AT, now)
            .set(ORGANIZATIONS.UPDATED_AT, now)
            .returning(ORGANIZATIONS.ID)
            .fetchOne()!!.id!!
    }

    override fun update(id: Id, name: String, parentOrganizationId: Id?) {
        dsl.update(ORGANIZATIONS)
            .set(ORGANIZATIONS.NAME, name)
            .set(ORGANIZATIONS.PARENT_ORGANIZATION_ID, parentOrganizationId)
            .set(ORGANIZATIONS.UPDATED_AT, LocalDateTime.now())
            .where(ORGANIZATIONS.ID.eq(id))
            .execute()
    }

    // 상위로 거슬러 올라간다. 데이터가 이미 순환이어도 멈추도록 본 id는 다시 따라가지 않는다
    override fun ancestorIds(id: Id): List<Id> {
        val parents = dsl.select(ORGANIZATIONS.ID, ORGANIZATIONS.PARENT_ORGANIZATION_ID).from(ORGANIZATIONS)
            .fetch().associate { it.value1()!! to it.value2() }
        val result = mutableListOf<Id>()
        var current = parents[id]
        while (current != null && current != id && current !in result) {
            result += current
            current = parents[current]
        }
        return result
    }

    override fun childCounts(ids: Collection<Id>): Map<Id, Int> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(ORGANIZATIONS.PARENT_ORGANIZATION_ID, DSL.count()).from(ORGANIZATIONS)
            .where(ORGANIZATIONS.PARENT_ORGANIZATION_ID.`in`(ids)).groupBy(ORGANIZATIONS.PARENT_ORGANIZATION_ID)
            .fetch().associate { it.value1()!! to it.value2()!! }
    }

    override fun programCounts(ids: Collection<Id>): Map<Id, Int> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(PROGRAMS.ORGANIZATION_ID, DSL.count()).from(PROGRAMS)
            .where(PROGRAMS.ORGANIZATION_ID.`in`(ids)).groupBy(PROGRAMS.ORGANIZATION_ID)
            .fetch().associate { it.value1()!! to it.value2()!! }
    }

    private fun load(rows: Result<OrganizationsRecord>): List<Organization> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id!! }
        val contacts = dsl.selectFrom(ORGANIZATION_CONTACTS)
            .where(ORGANIZATION_CONTACTS.ORGANIZATION_ID.`in`(ids))
            .orderBy(ORGANIZATION_CONTACTS.ID)
            .fetch()
            .groupBy({ it.organizationId!! }) {
                OrganizationContact(it.id!!, it.organizationId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return rows.map {
            Organization(
                id = it.id!!,
                name = it.name!!,
                contacts = contacts[it.id].orEmpty(),
                parentOrganizationId = it.parentOrganizationId,
                timestamps = Timestamps(it.createdAt!!, it.updatedAt!!),
                address = it.lat?.let { lat -> Address(lat, it.lng!!, it.addressLine1!!, it.addressLine2!!) },
            )
        }
    }
}
