package io.fixabley.dearby.entities.organization.api

import io.fixabley.dearby.entities.organization.model.OrganizationModel

internal fun interface OrganizationSource { fun find(id: String): OrganizationModel? }
internal class InMemoryOrganizationSource(records: List<OrganizationModel>) : OrganizationSource {
    private val records = records.associateBy { it.id }
    override fun find(id: String) = records[id]
}
