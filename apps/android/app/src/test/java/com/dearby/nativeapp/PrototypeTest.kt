package com.dearby.nativeapp

import com.dearby.nativeapp.app.CatalogViewModel
import com.dearby.nativeapp.entities.catalog.model.demoActivities
import com.dearby.nativeapp.features.calendar.*
import com.dearby.nativeapp.features.application.safeWebUrl
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PrototypeTest {
    @Test fun fixedCatalogIncludesScheduledMeetupAndSafeLinks() {
        val model = CatalogViewModel()
        assertEquals(listOf("conference", "camp", "meetup"), model.state.value.activities.map { it.id })
        assertEquals(listOf("모집 중", "모집 중", "모집 예정"), model.state.value.activities.map { it.status })
        assertTrue(demoActivities.all { it.schedule.timeZone == "Asia/Seoul" && it.url == "https://example.com" && safeWebUrl(it.url) })
        assertEquals("2026년 10월 24일 13:00~17:00", model.state.value.activities.first().date)
    }
    @Test fun filterAndReportSurviveNavigationButNotNewSession() {
        val model = CatalogViewModel()
        model.filter("선발형")
        assertEquals(listOf("camp"), model.state.value.visibleActivities.map { it.id })
        model.report("camp", true)
        model.filter("전체")
        assertTrue(model.state.value.activities.single { it.id == "camp" }.report)
        assertFalse(model.state.value.activities.first().report)
        model.report("camp", false)
        assertFalse(model.state.value.activities.any { it.report })
        model.report("camp", true)
        val fresh = CatalogViewModel().state.value
        assertEquals("전체", fresh.filter)
        assertFalse(fresh.activities.any { it.report })
    }
    @Test fun conferenceHasOneHourConflictOthersHaveNone() {
        val conflict = demoOverlaps(listOf(demoActivities[0].schedule)).single()
        assertEquals(Instant.parse("2026-10-24T05:00:00Z").toEpochMilli(), conflict.start)
        assertEquals(3_600_000L, conflict.end - conflict.start)
        assertTrue(demoOverlaps(listOf(demoActivities[1].schedule)).isEmpty())
        assertTrue(demoOverlaps(listOf(demoActivities[2].schedule)).isEmpty())
    }
    @Test fun touchingOrEmptyIntervalsDoNotOverlap() {
        val window = CalendarWindowState("test", 10, 20, "Asia/Seoul")
        assertFalse(calendarOverlaps(window, BusyTimeState(20, 30)))
        assertFalse(calendarOverlaps(window, BusyTimeState(0, 10)))
        assertFalse(calendarOverlaps(window, BusyTimeState(15, 15)))
        assertTrue(calendarOverlaps(window, BusyTimeState(15, 30)))
    }
    @Test fun applicationLinksRejectUnsafeSchemes() {
        assertTrue(safeWebUrl("https://example.com/dearby/camp"))
        listOf("javascript:alert(1)", "file:///data/local", "dearby://card/1").forEach { assertFalse(safeWebUrl(it)) }
    }
}
