package com.dearby.nativeapp

import com.dearby.nativeapp.CatalogFixture.CAMP
import com.dearby.nativeapp.CatalogFixture.CONFERENCE
import com.dearby.nativeapp.app.CatalogViewModel
import com.dearby.nativeapp.entities.catalog.api.parseCatalog
import com.dearby.nativeapp.pages.catalog.CatalogPhase
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private fun open() = parseCatalog(CatalogFixture.body()).single { it.id == CONFERENCE }
    private fun with(vararg override: Pair<String, String>) = parseCatalog(CatalogFixture.body(listOf(
        CatalogFixture.activity(CONFERENCE, "변형", day = "2026-10-24", start = "13:00", end = "17:00", override = override.toMap())))).single()
    private fun loaded() = CatalogViewModel { parseCatalog(CatalogFixture.body()) }.also { runBlocking { it.load() } }

    @Test fun parsingKeepsOrganizationsAndOnlyTimedSchedules() {
        assertEquals("테스트 주최", open().organization)
        assertEquals(1, open().schedules.size)
        val dateOnly = with("schedules" to "[{\"id\":\"x\",\"title\":\"x\",\"startAt\":null,\"endAt\":null,\"dateLabel\":\"10월 24일\",\"timeZone\":\"Asia/Seoul\"}]")
        assertTrue(dateOnly.schedules.isEmpty())
        assertThrows(Exception::class.java) { parseCatalog("{}") }
    }
    /** Same boundary cases as the web `isRecruiting` tests. */
    @Test fun recruitingMatchesTheWebRule() {
        assertTrue(open().isOpen(now))
        assertFalse(open().isOpen(Instant.parse("2025-12-31T23:59:59Z")))
        assertFalse(open().isOpen(Instant.parse("2099-01-01T00:00:00Z")))
        listOf("freshness" to "\"stale\"", "recruitmentStatus" to "\"closed\"", "isRecruiting" to "false",
            "recruitmentStartAt" to "\"2026-12-01T00:00:00Z\"", "recruitmentEndAt" to "\"2026-10-05T00:00:00Z\"",
            "validUntil" to "\"invalid\"", "validUntil" to "null", "sourceCheckedAt" to "null", "recruitmentStartAt" to "\"invalid\"",
        ).forEach { assertFalse(it.toString(), with(it).isOpen(now)) }
        assertTrue(with("recruitmentStartAt" to "null", "recruitmentEndAt" to "null").isOpen(now))
    }
    @Test fun quickApplyNeedsOpenRegistrationAndHttpsLink() {
        assertEquals(CatalogFixture.APPLY_URL, open().quickApplyUrl(now))
        assertNull(with("participationType" to "\"selection\"").quickApplyUrl(now))
        assertNotNull(with("participationType" to "\"selection\"").applyUrl(now))
        assertNull(with("applicationUrl" to "\"http://apply.example.test\"").quickApplyUrl(now))
        assertNull(with("applicationUrl" to "null").quickApplyUrl(now))
        assertNull(with("recruitmentStatus" to "\"scheduled\"").quickApplyUrl(now))
    }
    @Test fun discoveryShowsOnlyOpenActivitiesAndFailureIsNotEmptySuccess() {
        val state = loaded().state.value
        assertEquals(CatalogPhase.LOADED, state.phase)
        assertEquals(listOf(CONFERENCE, CAMP), state.visibleActivities.map { it.id })
        assertEquals(CatalogFixture.APPLY_URL, state.visibleActivities.first().quickApplyUrl)
        val failing = CatalogViewModel { error("offline") }
        runBlocking { failing.load() }
        assertEquals(CatalogPhase.FAILED, failing.state.value.phase)
        assertTrue(failing.state.value.visibleActivities.isEmpty())
    }
    @Test fun myActivitiesStartEmptyAndConfirmationNeedsApplication() {
        val model = loaded()
        assertTrue(model.state.value.appliedActivities.isEmpty())
        model.apply(CAMP, true)
        model.apply(CONFERENCE, true)
        assertEquals(listOf(CONFERENCE, CAMP), model.state.value.appliedActivities.map { it.id })
        model.confirm(CAMP, true)
        assertTrue(model.state.value.appliedActivities.single { it.id == CAMP }.confirmed)
        model.apply(CAMP, false); model.apply(CAMP, true)
        assertFalse(model.state.value.appliedActivities.single { it.id == CAMP }.confirmed)
        model.apply(CAMP, false); model.confirm(CAMP, true)
        assertFalse(model.state.value.activities.single { it.id == CAMP }.confirmed)
        // Marks survive a reload of the same catalog.
        model.apply(CONFERENCE, true)
        runBlocking { model.load() }
        assertTrue(model.state.value.activities.single { it.id == CONFERENCE }.applied)
    }
    @Test fun multiDaySchedulesShowDatesInTheirOwnZone() {
        val twoDays = "[" + listOf("2026-10-24T13:00:00+09:00" to "2026-10-24T17:00:00+09:00", "2026-10-25T10:00:00+09:00" to "2026-10-25T12:00:00+09:00")
            .joinToString(",") { (start, end) -> "{\"id\":\"x\",\"title\":\"세션\",\"startAt\":\"$start\",\"endAt\":\"$end\",\"dateLabel\":\"\",\"timeZone\":\"Asia/Seoul\"}" } + "]"
        val model = CatalogViewModel { parseCatalog(CatalogFixture.body(listOf(CatalogFixture.activity(CONFERENCE, "이틀", day = "2026-10-24", start = "13:00", end = "17:00", override = mapOf("schedules" to twoDays))))) }
        runBlocking { model.load() }
        assertEquals(listOf("10/24 13:00 – 17:00", "10/25 10:00 – 12:00"), model.state.value.activities.single().sessions.map { it.first })
        assertEquals(listOf("13:00 – 17:00"), loaded().state.value.activities.first().sessions.map { it.first })
    }
}
