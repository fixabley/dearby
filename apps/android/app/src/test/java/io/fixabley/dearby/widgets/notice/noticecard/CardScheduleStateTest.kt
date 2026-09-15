package io.fixabley.dearby.widgets.notice.noticecard

import io.fixabley.dearby.entities.notice.api.noticeFixture
import io.fixabley.dearby.entities.notice.model.*
import org.junit.Assert.*
import org.junit.Test

class CardScheduleStateTest {
    @Test fun datesKeepMissingBoundariesDateOnlyAndTimezones() {
        val dateOnly = cardPeriodText(null, "2026-09-11", null, "2026-10-07", "Asia/Seoul", "fallback")
        assertEquals("2026.9.11(금) (시간 미확인)부터 2026.10.7(수) (시간 미확인)까지", dateOnly)
        assertTrue(cardPeriodText(null, null, null, "2026-10-07", "Asia/Seoul", "fallback").startsWith("시작 미확인"))
        assertTrue(cardPeriodText(null, "2026-10-07", null, null, "Asia/Seoul", "fallback").endsWith("종료 미확인"))
        assertEquals("원문 안내", cardPeriodText(null, null, null, null, "Asia/Seoul", "원문 안내"))
        val abroad = cardPeriodText("2026-09-15T01:02:03.123Z", null, null, null, "America/New_York", "fallback")
        assertTrue(abroad.contains("2026.9.14(월) 21:02:03.123"))
        assertTrue(abroad.endsWith("(America/New_York)"))
    }
    @Test fun malformedConflictingAndReversedDatesRemainExplicit() {
        for (text in listOf(
            cardPeriodText("bad", "2026-09-15", null, null, "Asia/Seoul", "원문"),
            cardPeriodText(null, "2026-10-02", null, "2026-10-01", "Asia/Seoul", "원문"),
            cardPeriodText("2026-09-15T10:00:00Z", "2026-09-14", null, null, "Asia/Seoul", "원문"),
            cardPeriodText(null, "2026-09-15", null, null, "invalid", "원문")
        )) { assertTrue(text.startsWith("날짜 확인 필요")); assertTrue(text.contains("원문")) }
    }
    @Test fun everyPhaseUsesExactJoinOnlineUrlAndValidCoordinatesOnly() {
        val first = NoticeVenue("final", "결선 501호", "긴 주소", VenueCoordinates(0.0, 0.0))
        val second = first.copy(name = "두 번째", coordinates = VenueCoordinates(Double.NaN, 0.0))
        val notice = noticeFixture().copy(
            applicationInformation = NoticeApplication("신청", url = "https://apply.example", submissionLocations = listOf("제출 사무실")),
            schedules = listOf(NoticePhase("preliminary", mode = "online", onlineUrl = "https://online.example"),
                NoticePhase("final", mode = "hybrid", onlineUrl = "https://hybrid.example"), NoticePhase("missing")),
            location = NoticeLocation("전체 설명", "mixed", "partial", listOf(first, second, first.copy(phase = "preliminary"))))
        val rows = cardSchedules(notice)
        assertEquals(4, rows.size)
        assertEquals(listOf("제출 사무실", "apply.example"), rows[0].places.map { it.text })
        assertEquals(listOf(CardPlaceState("온라인 · online.example")), rows[1].places)
        assertEquals(3, rows[2].places.size)
        assertEquals(first, rows[2].places[1].mapVenue)
        assertNull(rows[2].places[2].mapVenue)
        assertEquals("장소 미확인", rows[3].places.single().text)
        assertFalse(rows.any { it.places.any { place -> place.text.contains(notice.sourceURL) } })
        assertEquals("신청 위치 미확인", cardSchedules(noticeFixture())[0].places.single().text)
    }
    @Test fun urlLabelsMatchDetailHostPolicyWithoutChangingRawUrlsOrAddresses() {
        val raw = "https://www.example.org:8443/apply/path?token=long#section"
        val mixed = "방문 안내 https://www.example.org/apply"
        val submissions = listOf("부산시 센텀로 123 501호", mixed, "https://user@example.org/path", "https://bad host/path", "mailto:apply@example.org", raw)
        val notice = noticeFixture().copy(applicationInformation = NoticeApplication("신청", url = raw, submissionLocations = submissions),
            schedules = listOf(NoticePhase("event", mode = "online", onlineUrl = raw)))
        val rows = cardSchedules(notice)
        assertEquals(submissions.dropLast(1) + "www.example.org", rows[0].places.map { it.text })
        assertEquals("온라인 · www.example.org", rows[1].places.single().text)
        assertEquals(raw, notice.applicationInformation.url)
        assertEquals(raw, notice.schedules.single().onlineUrl)
        assertEquals(submissions, notice.applicationInformation.submissionLocations)
    }

}
