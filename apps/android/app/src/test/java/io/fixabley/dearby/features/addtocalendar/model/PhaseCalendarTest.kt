package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.notice.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PhaseCalendarTest {
    private fun phaseDraft(notice: NoticeModel, phase: NoticePhase): CalendarDraft? {
        return phaseCalendarDraft(notice, phase)
    }

    @Test fun preciseKrcAndDbPhasesKeepKnownHours() {
        for ((start, end) in listOf("14:00:00" to "16:00:00", "10:00:00" to "17:00:00")) {
            val phase = NoticePhase("event", "2026-09-15T$start+09:00", "2026-09-15", "2026-09-15T$end+09:00")
            val draft = phaseDraft(calendarNoticeRecord(), phase)!!
            assertFalse(draft.allDay)
            assertEquals(java.time.OffsetDateTime.parse(phase.startsAt).toInstant().toEpochMilli(), draft.beginsAtMillis)
            assertEquals(java.time.OffsetDateTime.parse(phase.endsAt).toInstant().toEpochMilli(), draft.endsAtMillis)
            assertEquals("공고 [행사]", draft.title)
        }
    }

    @Test fun dateOnlyUnknownEndAndInclusiveRangeDoNotInventAnHour() {
        val phase = NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online")
        val draft = phaseDraft(calendarNoticeRecord(), phase)!!
        assertTrue(draft.allDay)
        assertEquals(Instant.parse("2026-10-14T00:00:00Z").toEpochMilli(), draft.beginsAtMillis)
        assertEquals(Instant.parse("2026-10-15T00:00:00Z").toEpochMilli(), draft.endsAtMillis)
        assertTrue(draft.description.contains("종료: 미확인"))
        val range = phaseDraft(calendarNoticeRecord(), phase.copy(endsOn = "2026-10-16"))!!
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), range.endsAtMillis)
        val midnight = phaseDraft(calendarNoticeRecord(), phase.copy(endsAt = "2026-10-16T00:00:00+09:00"))!!
        assertEquals(Instant.parse("2026-10-16T00:00:00Z").toEpochMilli(), midnight.endsAtMillis)
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(startsOn = null, endsOn = "2026-10-16")))
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(endsOn = "2026-10-13")))
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(startsAt = "invalid")))
    }

    @Test fun onlinePreliminaryNeverBorrowsFinalVenueAndOnlyVerifiedOnlineUrlIsIncluded() {
        val notice = calendarNoticeRecord().copy(location = NoticeLocation("온라인 예선 / 결선 장소", "mixed", "partial",
            listOf(NoticeVenue("final", "결선 전용 장소", "결선 주소", VenueCoordinates(1.0, 2.0)))))
        val phase = NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online", onlineUrl = "https://example.org/live?x=1&y=2#room")
        val draft = phaseDraft(notice, phase)!!
        assertEquals("온라인", draft.location)
        assertTrue(draft.description.contains("온라인 URL: ${phase.onlineUrl}"))
        assertFalse(draft.description.contains("결선 전용 장소"))
        assertFalse(draft.description.contains("maps/search"))
        for (url in listOf(null, "", "javascript:foo", "https:///bad", "https://example.org/ bad")) {
            val unknown = phaseDraft(notice, phase.copy(onlineUrl = url))!!
            assertEquals("온라인", unknown.location)
            assertFalse(unknown.description.contains("온라인 URL:"))
        }
    }

    @Test fun exactPhaseJoinRetainsAllMatchingVenuesAndSafeCoordinateMapLinks() {
        val notice = calendarNoticeRecord().copy(location = NoticeLocation("여러 장소", "mixed", "known", listOf(
            NoticeVenue("preliminary", "예선 전용", null, null),
            NoticeVenue("final", "한글 & # 결선 5층", "첫 주소", VenueCoordinates(0.0, 0.0)),
            NoticeVenue("final", "둘째 결선", "둘째 주소", VenueCoordinates(12.5, -45.0)),
            NoticeVenue(null, "단계 미지정", null, null),
        )))
        val phase = NoticePhase("final", startsOn = "2026-11-04", mode = "offline")
        val draft = phaseDraft(notice, phase)!!
        assertEquals("한글 & # 결선 5층 · 첫 주소 / 둘째 결선 · 둘째 주소", draft.location)
        assertFalse(draft.description.contains("예선 전용"))
        assertFalse(draft.description.contains("단계 미지정"))
        assertTrue(draft.description.contains("query=0.0%2C0.0"))
        assertTrue(draft.description.contains("query=12.5%2C-45.0"))
        assertEquals("장소 미확인", phaseDraft(notice, phase.copy(phase = "event"))!!.location)
    }
}
