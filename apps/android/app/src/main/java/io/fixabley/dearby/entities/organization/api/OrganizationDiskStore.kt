package io.fixabley.dearby.entities.organization.api

import io.fixabley.dearby.entities.organization.model.OrganizationModel

internal interface OrganizationDiskStore {
    fun find(id: String): OrganizationModel?
    fun upsert(value: OrganizationModel)
}

/** Worker-thread L2→mock L3 source; write failure propagates before repository L1 promotion. */
internal class StoredOrganizationSource(private val disk: OrganizationDiskStore, private val external: OrganizationSource) : OrganizationSource {
    override fun find(id: String): OrganizationModel? = disk.find(id)?.also { require(it.id == id) { "Stored ID mismatch" } }
        ?: external.find(id)?.also { require(it.id == id) { "External ID mismatch" }; disk.upsert(it) }
}
