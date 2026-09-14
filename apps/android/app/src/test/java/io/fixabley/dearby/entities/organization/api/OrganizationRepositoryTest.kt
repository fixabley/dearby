package io.fixabley.dearby.entities.organization.api

import io.fixabley.dearby.entities.organization.model.OrganizationModel
import org.junit.Assert.*
import org.junit.Test

class OrganizationRepositoryTest {
    @Test fun coldMissThenCacheHitFetchesOnlySuccessfulRecords() {
        var calls = 0
        val repo = OrganizationRepository(OrganizationSource { calls++; if (it == "selected") OrganizationModel(it, "선택 조직", null) else null })
        assertEquals(0, calls)
        assertEquals("selected", repo.find("selected")!!.id)
        repo.find("selected")
        assertEquals(1, calls)
        assertNull(repo.find(null))
        repeat(2) { assertNull(repo.find("missing")) }
        assertEquals(3, calls)
    }

    @Test fun sharedParentsAreFetchedOnceAcrossRelatedPaths() {
        val records = listOf(OrganizationModel("a", "A", "parent"), OrganizationModel("b", "B", "parent"), OrganizationModel("parent", "부모", null))
        val fetches = mutableListOf<String>()
        val repo = OrganizationRepository(OrganizationSource { id -> fetches.add(id); records.find { it.id == id } })
        assertEquals(listOf("parent", "a"), repo.path("a").map { it.id })
        assertEquals(listOf("parent", "b"), repo.path("b").map { it.id })
        repo.path("a")
        assertEquals(listOf("a", "parent", "b"), fetches)
    }

    @Test fun missingParentAndCyclesReturnRecoverablePaths() {
        val repo = OrganizationRepository(InMemoryOrganizationSource(listOf(
            OrganizationModel("a", "A", "b"), OrganizationModel("b", "B", "a"), OrganizationModel("partial", "부분", "missing"))))
        assertEquals(listOf("b", "a"), repo.path("a").map { it.id })
        assertEquals(listOf("partial"), repo.path("partial").map { it.id })
        assertTrue(repo.path("missing").isEmpty())
        assertTrue(repo.path(null).isEmpty())
    }

    @Test fun replacementInvalidatesSelectedAndAllAncestors() {
        val repo = OrganizationRepository(InMemoryOrganizationSource(listOf(OrganizationModel("a", "옛 이름", "old"), OrganizationModel("old", "옛 부모", null))))
        repo.path("a")
        repo.replaceSource(InMemoryOrganizationSource(listOf(OrganizationModel("a", "새 이름", "new"), OrganizationModel("new", "새 부모", null))))
        assertEquals(listOf("새 부모", "새 이름"), repo.path("a").map { it.name })
        assertNull(repo.find("old"))
    }
}
