package com.dearby.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.catalog.api.CatalogRepository
import com.dearby.nativeapp.entities.catalog.model.CatalogLocalModel
import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.storage.DearbyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.io.File

/** Opt-in, actual coordinator-owned API with officially collected catalog; no fixture fallback. */
class CatalogApiIntegrationTest {
    @Test fun realPublicCatalogAndRoomRoundTrip() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogApi") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "catalog-api-${System.nanoTime()}.db"
        var database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val http = HttpClient(BuildConfig.API_BASE_URL, BuildConfig.DEBUG) { error("Public endpoint must not read token") }
        val repository = CatalogRepository(http, database.dao())
        val result = repository.refresh()
        assertTrue(result.activities.isNotEmpty())
        val current = result.activities.filter { it.current(Instant.now()) }
        assertTrue(current.isNotEmpty())
        val selected = current.first()
        val saved = CatalogLocalModel(setOf(selected.programId), setOf(selected.organizationId), mapOf(selected.id to "applied"))
        repository.save(saved)
        database.close()
        database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val restored = CatalogRepository(http, database.dao())
        assertEquals(result, restored.cached()); assertEquals(saved, restored.local())
        val folder = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "real-catalog-api.txt").writeText("Actual public GET /v1/catalog passed\nActivities: ${result.activities.size}\nCurrent: ${current.size}\nSelected: ${selected.title}\nGenerated: ${result.generatedAt}\nRoom reopen: catalog/bookmarks/report passed\nNo authentication or application submission\n")
        database.close(); context.deleteDatabase(name); Unit
    }
}
