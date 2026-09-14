package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.Organization

internal fun interface OrganizationSource {
    fun find(id: String): Organization?
}

/** Snapshot source storage, independent of the initially empty read cache. */
internal class InMemoryOrganizationSource(records: List<Organization>) : OrganizationSource {
    private val recordsById = records.associateBy { it.id }
    override fun find(id: String): Organization? = recordsById[id]
}
