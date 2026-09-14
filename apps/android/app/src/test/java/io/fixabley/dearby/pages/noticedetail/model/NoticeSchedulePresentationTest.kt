package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import org.junit.Assert.*
import org.junit.Test

class NoticeSchedulePresentationTest {
    @Test fun onlinePhaseNeverDisplaysAnOfflineVenueAndPreservesItsUrl() {
        val state = NoticeScheduleState(NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online",
            onlineUrl = "https://example.org/round"), listOf(NoticeVenue("final", "본선", "본선 주소", null)))
        assertEquals("온라인 예선", state.title)
        assertEquals("온라인", state.placeText)
        assertEquals("온라인 · https://example.org/round", state.placeDescription)
        assertEquals("온라인 예선", state.copy(period = state.period.copy(phase = "온라인 예선")).title)
        assertTrue(state.dateText.contains("시간 미확인"))
        assertTrue(state.dateDescription.contains("2026-10-14"))
        assertFalse(state.placeText.contains("본선"))
    }
    @Test fun multipleVenuesRetainNamesAddressesAndRooms() {
        val state = NoticeScheduleState(NoticePhase("final", startsOn = "2026-11-04"),
            listOf(NoticeVenue("final", "A관 217호", "충북 청주시 A로 1", null),
                NoticeVenue("final", "B관 5층", "충북 청주시 B로 2", null)))
        assertEquals("결선·시상", state.title)
        assertEquals("A관 217호 · 충북 청주시 A로 1 / B관 5층 · 충북 청주시 B로 2", state.placeDescription)
        assertEquals("장소 미확인", state.copy(locations = emptyList()).placeText)
    }
}
