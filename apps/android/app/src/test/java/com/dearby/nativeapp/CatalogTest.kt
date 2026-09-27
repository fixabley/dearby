package com.dearby.nativeapp

import androidx.lifecycle.ViewModelStore
import com.dearby.nativeapp.app.providers.activityStates
import com.dearby.nativeapp.app.providers.catalogDate
import com.dearby.nativeapp.app.CatalogViewModel
import com.dearby.nativeapp.entities.catalog.api.CatalogRepository
import com.dearby.nativeapp.entities.catalog.model.*
import com.dearby.nativeapp.shared.api.*
import com.dearby.nativeapp.shared.storage.*
import com.dearby.nativeapp.features.application.safeWebUrl
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

private val checked = Instant.parse("2026-09-27T00:00:00Z")
private fun fixture() = CatalogModel(checked.toString(), listOf(OrganizationModel("o", "조직", "")), listOf(ProgramModel("p", "o", "프로그램", "")), listOf(ActivityModel("a", "p", "o", "활동", "소개", "selection", "open", true, null, null, "10월 중", null, null, null, null, emptyList(), emptyList(), "https://example.org", "https://example.org/apply", checked.toString(), checked.plusSeconds(3600).toString(), "verified", "공식 출처")))

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogTest {
    @Test fun statusAndDatesRemainHonestAfterDeadlineAndAcrossTimezones() {
        val catalog = fixture()
        val expired = catalog.copy(activities = catalog.activities.map { it.copy(recruitmentEndAt = checked.toString()) })
        assertEquals("모집 종료", expired.activityStates(CatalogLocalModel(), checked).single().status)
        val scheduled = catalog.copy(activities = catalog.activities.map { it.copy(isRecruiting = false, recruitmentStatus = "scheduled") })
        assertEquals("모집 예정", scheduled.activityStates(CatalogLocalModel(), checked).single().status)
        assertTrue(catalogDate(checked.toString(), "Asia/Seoul").contains("09:00"))
        assertTrue(catalogDate(checked.toString(), "UTC").contains("00:00"))
    }
    @Test fun rejectsDuplicateAndCrossOrganizationReferences() {
        val value = fixture()
        assertTrue(runCatching { value.copy(activities = value.activities + value.activities).validated() }.isFailure)
        assertTrue(runCatching { value.copy(programs = value.programs.map { it.copy(organizationId = "missing") }).validated() }.isFailure)
        assertTrue(runCatching { value.copy(activities = value.activities.map { it.copy(organizationId = "missing") }).validated() }.isFailure)
    }
    @Test fun cacheIsScopedToNormalizedServerOrigin() = runTest {
        val dao = MemoryDao()
        val one = HttpClient("https://EXAMPLE.org/", false) { null }
        val two = HttpClient("https://another.example.org", false) { null }
        assertEquals("https://example.org", one.cacheNamespace)
        dao.put(DocumentRecord("catalog:v1:${one.cacheNamespace}", wireJson.encodeToString(fixture())))
        assertNotNull(CatalogRepository(one, dao).cached())
        assertNull(CatalogRepository(two, dao).cached())
    }
    @Test fun freshnessExpiresAtBoundaryAndNeverRenewsOnFetch() {
        val activity = fixture().activities.single()
        assertTrue(activity.current(checked))
        assertFalse(activity.current(checked.minusSeconds(1)))
        assertFalse(activity.current(checked.plusSeconds(3600)))
        assertFalse(activity.copy(freshness = "stale").current(checked))
        assertFalse(activity.copy(isRecruiting = false).current(checked))
        assertFalse(activity.copy(recruitmentStatus = "unknown").current(checked))
        assertFalse(activity.copy(recruitmentStartAt = checked.plusSeconds(1).toString()).current(checked))
        assertFalse(activity.copy(recruitmentEndAt = checked.toString()).current(checked))
        assertFalse(activity.copy(validUntil = "broken").current(checked))
        assertFalse(activity.copy(validUntil = checked.plusSeconds(172800).toString()).current(checked.plusSeconds(86400)))
    }
    @Test fun safeLinksRejectCredentialsSchemesAndMalformedUrls() {
        listOf("javascript:alert(1)", "file:///tmp/a", "intent://login", "https://user:pass@example.org", "https:///a", "https://example.org\n").forEach { assertFalse(it, safeWebUrl(it)) }
        assertTrue(safeWebUrl("https://example.org/apply?q=1#form"))
    }
    @Test fun publicHttpContractCacheFailureAndEmptyAreDistinct() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var status = 200
        var response = wireJson.encodeToString(fixture())
        server.createContext("/v1/catalog") { exchange ->
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("GET", exchange.requestMethod)
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val dao = MemoryDao(); val http = HttpClient("http://127.0.0.1:${server.address.port}", true) { "must-not-send" }
            val repository = CatalogRepository(http, dao)
            assertEquals(fixture(), repository.refresh())
            status = 503
            assertTrue(runCatching { repository.refresh() }.isFailure)
            assertEquals(fixture(), repository.cached())
            assertEquals(fixture(), CatalogRepository(http, dao).cached())
            status = 200; response = wireJson.encodeToString(fixture().copy(activities = emptyList()))
            assertTrue(repository.refresh().activities.isEmpty())
        } finally { server.stop(0) }
    }
    @Test fun refreshStorageFailureDoesNotPublishFetchedContent() = runBlocking {
        val memory = MemoryDao()
        memory.put(DocumentRecord("catalog:v1:", wireJson.encodeToString(fixture())))
        val broken = object : DearbyDao by memory { override suspend fun put(record: DocumentRecord) { error("disk full") } }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/catalog") { exchange ->
            val bytes = wireJson.encodeToString(fixture().copy(activities = emptyList())).toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val http = HttpClient("http://127.0.0.1:${server.address.port}", true) { null }
            memory.put(DocumentRecord("catalog:v1:${http.cacheNamespace}", wireJson.encodeToString(fixture())))
            val repository = CatalogRepository(http, broken)
            assertEquals(fixture(), repository.cached())
            assertTrue(runCatching { repository.refresh() }.isFailure)
            assertEquals(fixture(), repository.cached())
        } finally { server.stop(0) }
    }
    @Test fun corruptCacheStillRecoversFromNetworkWithoutErasingLocalSaves() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/catalog") { exchange ->
            val bytes = wireJson.encodeToString(fixture()).toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val memory = MemoryDao()
            val http = HttpClient("http://127.0.0.1:${server.address.port}", true) { null }
            memory.put(DocumentRecord("catalog:v1:${http.cacheNamespace}", "broken JSON"))
            val repository = CatalogRepository(http, memory)
            repository.save(CatalogLocalModel(programs = setOf("p")))
            val model = CatalogViewModel(repository) { checked }; store.put("catalog", model)
            model.state.first { !it.loading }
            assertNull(model.state.value.error)
            assertTrue(model.state.value.activities.single().programSaved)
            assertEquals(fixture(), repository.cached())
        } finally { store.clear(); Dispatchers.resetMain(); server.stop(0) }
    }
    @Test fun unreadableLocalStateCannotBeOverwritten() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val memory = MemoryDao()
            memory.put(DocumentRecord("catalog:local:v1:", "broken JSON"))
            val repository = CatalogRepository(HttpClient("", true) { null }, memory)
            val model = CatalogViewModel(repository) { checked }; store.put("catalog", model)
            model.state.first { !it.loading }
            assertFalse(model.state.value.storageReady)
            model.toggleProgram("p"); runCurrent()
            assertEquals("broken JSON", memory.document("catalog:local:v1:")!!.json)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun savedStatePublishesOnlyAfterCommitAndFailuresRetainPriorReport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val memory = MemoryDao()
            memory.put(DocumentRecord("catalog:v1:", wireJson.encodeToString(fixture())))
            var gate: CompletableDeferred<Unit>? = null
            var fail = false
            val dao = object : DearbyDao by memory {
                override suspend fun put(record: DocumentRecord) { gate?.await(); if (fail) error("disk full"); memory.put(record) }
            }
            val repo = CatalogRepository(HttpClient("", true) { null }, dao)
            var time = checked
            val model = CatalogViewModel(repo) { time }; store.put("catalog", model)
            model.state.first { !it.loading }
            assertTrue(model.state.value.cached)
            assertTrue(model.state.value.activities.single().current)
            gate = CompletableDeferred()
            model.toggleProgram("p"); runCurrent()
            assertFalse(model.state.value.activities.single().programSaved)
            gate!!.complete(Unit); runCurrent()
            assertTrue(model.state.value.activities.single().programSaved)
            gate = null
            model.report("a", "applied") {}; runCurrent()
            assertEquals("applied", model.state.value.activities.single().report)
            fail = true
            model.report("a", "not_applied") {}; runCurrent()
            assertEquals("applied", model.state.value.activities.single().report)
            assertNotNull(model.state.value.storageError)
            assertEquals("applied", repo.local().reports["a"])
            time = checked.plusSeconds(3600); advanceTimeBy(3_600_000); runCurrent()
            assertFalse(model.state.value.activities.single().current)
            assertTrue(model.state.value.activities.single().programSaved)
            assertEquals(setOf("p"), CatalogRepository(HttpClient("", true) { null }, memory).local().programs)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
