package io.fixabley.dearby

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.entities.activitycatalog.api.AssetCatalogProvider
import io.fixabley.dearby.features.favoriteorganization.api.SharedPreferencesFavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import org.junit.Assert.*
import org.junit.Test

class LocalDataTest {
    @Test
    fun realPreferencesRestoreLegacyFormatAndPersistChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Isolated real XML preferences: never clear the user's production file here.
        val name = "dearby.favorites.test.${System.nanoTime()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            // Seed the literal pre-refactor key rather than the adapter constant.
            assertTrue(preferences.edit().putStringSet("organizationIDs", setOf("cbnu-career", "krc")).commit())
            fun restore() = FavoritesState(SharedPreferencesFavoriteStore(
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
            ))
            val state = restore()
            assertEquals(setOf("cbnu-career", "krc"), state.ids)
            state.save("db-insurance")
            state.save("db-insurance")
            state.remove("krc")
            // A synchronous barrier waits for preceding apply() writes to reach disk.
            assertTrue(preferences.edit().commit())
            assertEquals(setOf("cbnu-career", "db-insurance"), restore().ids)
            val xml = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertTrue(xml.contains("organizationIDs"))
            assertTrue(xml.contains("cbnu-career"))
            assertTrue(xml.contains("db-insurance"))
            assertFalse(xml.contains("<string>krc</string>"))
            val restored = restore()
            restored.remove("cbnu-career")
            restored.remove("db-insurance")
            assertTrue(preferences.edit().commit())
            assertTrue(restore().ids.isEmpty())
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun bundledProviderPreservesTargetsContextParentAndEdition() {
        val catalog = AssetCatalogProvider(
            InstrumentationRegistry.getInstrumentation().targetContext.assets
        ).load()
        assertEquals(4, catalog.feed.size)
        val career = catalog.feed.first { it.id == "cieat-NCR000000007344" }
        assertEquals("krc", career.organizationId)
        assertEquals("채용 › 채용행사", career.categorySummary)
        assertEquals("충북대학교", catalog.contextNames(career))
        assertEquals(listOf("krc"), catalog.organizationPath(career.organizationId).map { it.id })
        val contest = catalog.feed.first { it.id == "cbnu-software-1154064" }
        assertEquals(2, contest.edition)
        assertEquals(listOf("yeongnam-ai-security", "yeongnam-cyber-defense"),
            catalog.organizationPath(contest.organizationId).map { it.id })
    }
}
