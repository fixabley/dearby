package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATION_CONTACTS
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.*
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqOrganizationRepository(private val dsl: DSLContext) : OrganizationRepository {

    override fun findAllById(ids: Collection<Id>): List<Organization> {
        if (ids.isEmpty()) return emptyList()
        val contacts = dsl.selectFrom(ORGANIZATION_CONTACTS)
            .where(ORGANIZATION_CONTACTS.ORGANIZATION_ID.`in`(ids))
            .orderBy(ORGANIZATION_CONTACTS.ID)
            .fetch()
            .groupBy({ it.organizationId!! }) {
                OrganizationContact(it.id!!, it.organizationId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return dsl.selectFrom(ORGANIZATIONS).where(ORGANIZATIONS.ID.`in`(ids)).fetch().map {
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
