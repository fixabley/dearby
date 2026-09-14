package io.fixabley.dearby.entities.organization.api

import io.fixabley.dearby.entities.organization.model.OrganizationModel
import org.junit.Assert.*
import org.junit.Test

class StoredOrganizationSourceTest {
    @Test fun memoryDiskExternalOrderAndFailedWriteDoesNotPromote() {
        val record = OrganizationModel("org", "조직", null)
        var reads = 0; var writes = 0; var fetches = 0; var failWrite = true
        var stored: OrganizationModel? = null
        val disk = object : OrganizationDiskStore {
            override fun find(id: String): OrganizationModel? { reads++; return stored }
            override fun upsert(value: OrganizationModel) { writes++; if (failWrite) error("disk full"); stored = value }
        }
        val source = StoredOrganizationSource(disk, OrganizationSource { fetches++; record })
        val repository = OrganizationRepository(source)
        assertThrows(IllegalStateException::class.java) { repository.find("org") }
        assertNull(stored)
        failWrite = false
        assertEquals(record, repository.find("org")); assertEquals(record, repository.find("org"))
        assertEquals(2, reads); assertEquals(2, fetches); assertEquals(2, writes)
        assertEquals(record, OrganizationRepository(source).find("org"))
        assertEquals(3, reads); assertEquals(2, fetches)
    }

    @Test fun readAndExternalFailuresPropagateAndMissingIsNotCached() {
        var fetches = 0
        val broken = object : OrganizationDiskStore {
            override fun find(id: String): OrganizationModel? = error("corrupt")
            override fun upsert(value: OrganizationModel) = error("unexpected")
        }
        val repository = OrganizationRepository(StoredOrganizationSource(broken, OrganizationSource { fetches++; null }))
        repeat(2) { assertThrows(IllegalStateException::class.java) { repository.find("missing") } }
        assertEquals(0, fetches)
        val empty = object : OrganizationDiskStore {
            override fun find(id: String): OrganizationModel? = null
            override fun upsert(value: OrganizationModel) = error("unexpected")
        }
        val missing = OrganizationRepository(StoredOrganizationSource(empty, OrganizationSource { fetches++; null }))
        repeat(2) { assertNull(missing.find("missing")) }; assertEquals(2, fetches)
        val failed = OrganizationRepository(StoredOrganizationSource(empty, OrganizationSource { error("mock failed") }))
        assertThrows(IllegalStateException::class.java) { failed.find("org") }
    }
    @Test fun mismatchedIdsNeverWriteOrReturnSuccess() {
        val value = OrganizationModel("wrong", "조직", null)
        var writes = 0
        val disk = object : OrganizationDiskStore {
            override fun find(id: String): OrganizationModel? = null
            override fun upsert(value: OrganizationModel) { writes++ }
        }
        assertThrows(IllegalArgumentException::class.java) { StoredOrganizationSource(disk, OrganizationSource { value }).find("requested") }
        assertEquals(0, writes)
        val corrupt = object : OrganizationDiskStore {
            override fun find(id: String): OrganizationModel? = value
            override fun upsert(value: OrganizationModel) { writes++ }
        }
        assertThrows(IllegalArgumentException::class.java) { StoredOrganizationSource(corrupt, OrganizationSource { error("must not fetch") }).find("requested") }
    }
}
