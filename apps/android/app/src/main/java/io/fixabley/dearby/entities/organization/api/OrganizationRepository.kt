package io.fixabley.dearby.entities.organization.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import io.fixabley.dearby.entities.organization.model.OrganizationModel

/** Independent lazy cache; App replaces sources on its UI-thread snapshot boundary. */
internal class OrganizationRepository(private var source: OrganizationSource) {
    private val cache = mutableMapOf<String, OrganizationModel>()
    var revision by mutableIntStateOf(0)
        private set
    @Synchronized fun find(id: String?): OrganizationModel? {
        if (id == null) return null
        return cache[id] ?: source.find(id)?.also { cache[id] = it }
    }
    @Synchronized fun replaceSource(replacement: OrganizationSource) {
        source = replacement
        cache.clear()
        revision++
    }

    @Synchronized fun path(id: String?): List<OrganizationModel> {
        val records = mutableListOf<OrganizationModel>()
        val seen = mutableSetOf<String>()
        var next = id
        while (next != null && seen.add(next)) {
            val record = find(next) ?: break
            records.add(0, record)
            next = record.parentId
        }
        return records
    }
}
