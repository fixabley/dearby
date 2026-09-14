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
        val range = phaseDraft(calendarNoticeRecord(), phase.copy(endsOn = "2026-10-16"))!!
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), range.endsAtMillis)
        val midnight = phaseDraft(calendarNoticeRecord(), phase.copy(endsAt = "2026-10-16T00:00:00+09:00"))!!
        assertEquals(Instant.parse("2026-10-16T00:00:00Z").toEpochMilli(), midnight.endsAtMillis)
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(startsOn = null, endsOn = "2026-10-16")))
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(endsOn = "2026-10-13")))
        assertNull(phaseDraft(calendarNoticeRecord(), phase.copy(startsAt = "invalid")))
    }

    @Test fun onlinePreliminaryKeepsLocationAndSourceOnlyDescription() {
        val notice = calendarNoticeRecord().copy(location = NoticeLocation("온라인 예선 / 결선 장소", "mixed", "partial",
            listOf(NoticeVenue("final", "결선 전용 장소", "결선 주소", VenueCoordinates(1.0, 2.0)))))
        val phase = NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online", onlineUrl = "https://example.org/live?x=1&y=2#room")
        val draft = phaseDraft(notice, phase)!!
        assertEquals("온라인", draft.location)
        assertEquals(notice.sourceURL, draft.description)
        for (url in listOf("", "javascript:foo", "https:///bad", "https://example.org/ bad")) {
            val unknown = phaseDraft(notice, phase.copy(onlineUrl = url))!!
            assertEquals("온라인", unknown.location)
            assertEquals(notice.sourceURL, unknown.description)
        }
    }

    @Test fun exactPhaseJoinRetainsAllMatchingVenuesAndSourceOnlyDescription() {
        val notice = calendarNoticeRecord().copy(location = NoticeLocation("여러 장소", "mixed", "known", listOf(
            NoticeVenue("preliminary", "예선 전용", null, null),
            NoticeVenue("final", "한글 & # 결선 5층", "첫 주소", VenueCoordinates(0.0, 0.0)),
            NoticeVenue("final", "둘째 결선", "둘째 주소", VenueCoordinates(12.5, -45.0)),
            NoticeVenue(null, "단계 미지정", null, null),
        )))
        val phase = NoticePhase("final", startsOn = "2026-11-04", mode = "offline")
        val draft = phaseDraft(notice, phase)!!
        assertEquals("한글 & # 결선 5층 · 첫 주소 / 둘째 결선 · 둘째 주소", draft.location)
        assertEquals(notice.sourceURL, draft.description)
        assertEquals("장소 미확인", phaseDraft(notice, phase.copy(phase = "event"))!!.location)
    }
    @Test fun phaseDescriptionAcceptsOnlySourceHttpUrlsWithoutOnlineFallback() {
        val phase = NoticePhase("event", startsOn = "2026-10-14", mode = "online", onlineUrl = "https://example.org/live")
        for (url in listOf("https://example.org/source?q=%ED%95%9C&x=1#room", "http://example.org/source")) {
            assertEquals(url, phaseDraft(calendarNoticeRecord().copy(sourceURL = url), phase)!!.description)
        }
        for (url in listOf("", "javascript:foo", "https:///bad", "https://user:pass@example.org/", "https://example.org/ bad")) {
            assertEquals("", phaseDraft(calendarNoticeRecord().copy(sourceURL = url), phase)!!.description)
        }
    }
}
