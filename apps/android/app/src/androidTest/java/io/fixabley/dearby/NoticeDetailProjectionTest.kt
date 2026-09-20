package io.fixabley.dearby

import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.app.data.decodeNoticeEvidence
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NoticeDetailProjectionTest {
    @Test fun canonicalDetailsDecodeAllSourcesFieldEvidenceAndApplicationMethods() {
        val favorites = io.fixabley.dearby.features.favoriteorganization.model.FavoritesState(object : io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore {
            override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {}
        })
        val repo = NoticeSession(AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets), favorites)
        val catalog = kotlinx.coroutines.runBlocking { repo.load() }
        val detail = repo.detail("cieat-NCR000000007344").state!!
        val raw = catalog.notices.first { it.id == detail.id }
        assertTrue(raw.sources.map { it.id }.containsAll(listOf("krc", "cbnu-campus-map", "cbnu-library-location")))
        assertTrue(raw.sources.all { it.url != null && it.checkedAt != null })
        assertTrue(raw.evidence.map { it.fieldPath }.containsAll(listOf("audience", "eligibility", "application", "schedule[0]", "location", "location.venues[0].coordinates", "benefits[0]", "schedule.duration")))
        assertTrue(raw.evidence.all { it.sourceId.isNotBlank() && it.locator.isNotBlank() && it.sourceURL != null })
        assertEquals(listOf("platform"), detail.applicationInformation.methods)
        assertEquals(raw.aiDescription, detail.aiDescription)
        assertEquals(raw.applicationInformation.summary, detail.applicationInformation.summary)
        assertEquals(raw.schedules.first(), detail.schedules.first().period)
        assertEquals(raw.location.venues, detail.schedules.first().locations)
        assertEquals(raw.sourceURL, detail.sourceURL)
    }

    @Test fun unknownSourceEvidenceKeepsIdLocatorAndPreciseFieldAssociationWithoutInventingUrl() {
        val json = JSONObject("""{"application":{"evidence":[{"sourceId":"missing","locator":"방법"}]},
            "schedule":[{"evidence":[{"sourceId":"known","locator":"날짜"}]}],
            "location":{"venues":[{"coordinateEvidence":[{"sourceId":"known","locator":"좌표"}]}]}}""")
        val before = json.toString()
        val evidence = decodeNoticeEvidence(json, mapOf("known" to "https://example.org/verified"))
        assertEquals(before, json.toString())
        assertEquals(3, evidence.size)
        val missing = evidence.single { it.sourceId == "missing" }
        assertEquals("application", missing.fieldPath)
        assertEquals("방법", missing.locator)
        assertNull(missing.sourceURL)
        assertEquals(setOf("schedule[0]", "location.venues[0].coordinates"), evidence.filter { it.sourceId == "known" }.map { it.fieldPath }.toSet())
    }
}
