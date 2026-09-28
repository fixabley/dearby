package com.dearby.nativeapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.catalog.api.CatalogRepository
import com.dearby.nativeapp.entities.catalog.model.CatalogLocalModel
import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.storage.DearbyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogPersistenceTest {
    @Test fun bookmarksAndExplicitReportSurviveRoomReopenAndFailedCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "catalog-${System.nanoTime()}.db"
        var database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val http = HttpClient("https://example.invalid", false) { null }
        val value = CatalogLocalModel(setOf("program"), setOf("organization"), mapOf("activity" to "applied"))
        CatalogRepository(http, database.dao()).save(value)
        database.close()
        database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        val repository = CatalogRepository(http, database.dao())
        assertEquals(value, repository.local())
        database.close()
        assertTrue(runCatching { repository.save(value.copy(reports = mapOf("activity" to "not_applied"))) }.isFailure)
        database = Room.databaseBuilder(context, DearbyDatabase::class.java, name).build()
        assertEquals(value, CatalogRepository(http, database.dao()).local())
        database.close(); context.deleteDatabase(name); Unit
    }
}
