package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.Organization

/** Monitor confinement makes lookup/path/replacement atomic, including all ancestor records. */
internal class OrganizationRepository(private var source: OrganizationSource) {
    private val cache = mutableMapOf<String, Organization>()

    @Synchronized fun find(id: String?): Organization? {
        if (id == null) return null
        cache[id]?.let { return it }
        return source.find(id)?.also { cache[id] = it }
    }

    @Synchronized fun path(id: String?): List<Organization> {
        val result = mutableListOf<Organization>()
        val seen = mutableSetOf<String>()
        var currentId = id
        while (currentId != null && seen.add(currentId)) {
            val record = find(currentId) ?: break
            result.add(0, record)
            currentId = record.parentOrganizationId
        }
        return result
    }

    @Synchronized fun replaceSource(replacement: OrganizationSource) {
        source = replacement
        cache.clear()
    }
}
