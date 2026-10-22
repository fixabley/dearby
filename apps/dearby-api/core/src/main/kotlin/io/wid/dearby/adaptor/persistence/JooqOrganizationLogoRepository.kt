package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATION_LOGOS
import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.OrganizationLogo
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqOrganizationLogoRepository(private val dsl: DSLContext) : OrganizationLogoRepository {

    override fun find(organizationId: Id): OrganizationLogo? =
        dsl.selectFrom(ORGANIZATION_LOGOS).where(ORGANIZATION_LOGOS.ORGANIZATION_ID.eq(organizationId)).fetchOne()
            ?.let { OrganizationLogo(it.organizationId!!, it.contentType!!, it.data!!, it.updatedAt!!) }

    override fun updatedAtByOrganization(organizationIds: Collection<Id>): Map<Id, OffsetDateTime> {
        if (organizationIds.isEmpty()) return emptyMap()
        return dsl.select(ORGANIZATION_LOGOS.ORGANIZATION_ID, ORGANIZATION_LOGOS.UPDATED_AT)
            .from(ORGANIZATION_LOGOS)
            .where(ORGANIZATION_LOGOS.ORGANIZATION_ID.`in`(organizationIds))
            .fetch { it.value1()!! to it.value2()!! }
            .toMap()
    }

    override fun save(logo: OrganizationLogo) {
        dsl.insertInto(ORGANIZATION_LOGOS)
            .set(ORGANIZATION_LOGOS.ORGANIZATION_ID, logo.organizationId)
            .set(ORGANIZATION_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(ORGANIZATION_LOGOS.DATA, logo.data)
            .set(ORGANIZATION_LOGOS.UPDATED_AT, logo.updatedAt)
            .onConflict(ORGANIZATION_LOGOS.ORGANIZATION_ID)
            .doUpdate()
            .set(ORGANIZATION_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(ORGANIZATION_LOGOS.DATA, logo.data)
            .set(ORGANIZATION_LOGOS.UPDATED_AT, logo.updatedAt)
            .execute()
    }

    override fun delete(organizationId: Id) {
        dsl.deleteFrom(ORGANIZATION_LOGOS).where(ORGANIZATION_LOGOS.ORGANIZATION_ID.eq(organizationId)).execute()
    }
}
