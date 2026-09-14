package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.noticecatalog.model.*
import io.fixabley.dearby.entities.noticecatalog.api.NoticeDetailRepository
import io.fixabley.dearby.entities.noticecatalog.api.CatalogProvider
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

internal fun calendarNoticeRecord(application: NoticeApplication = NoticeApplication("기존 신청 안내")) = Notice(
    "fixture", "공고", "원문 요약", null, "대상", "조건", application,
    NoticeLocation("장소 안내", "unknown", "unknown", emptyList()), emptyList(), emptyList(), emptyList(),
    "https://example.org/source", emptyList(), emptyList(), null,
)

internal fun projectCalendarNotice(notice: Notice): NoticeDetail {
    val repository = NoticeDetailRepository(CatalogProvider { NoticeCatalog("fixture", emptyList(), listOf(notice)) })
    repository.load()
    return repository.detail(notice.id)!!
}
internal fun calendarNotice(application: NoticeApplication = NoticeApplication("기존 신청 안내")) = projectCalendarNotice(calendarNoticeRecord(application))

class ApplicationCalendarTest {
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun preciseApplicationIntervalUsesKstDespiteDeviceTimezone() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(millis("2026-09-11T00:00:00Z"), calendarPeriod(null, "2026-09-11", null, null, "Asia/Seoul", true)!!.begin)
            for ((open, close) in listOf("2026-08-27T09:00:00+09:00" to "2026-09-15T13:00:00+09:00",
                "2026-08-06T09:00:00+09:00" to "2026-09-15T16:00:00+09:00")) {
                val draft = applicationCalendarDraft(calendarNotice(NoticeApplication("마감 안내", opensAt = open, closesAt = close)))!!
                assertFalse(draft.allDay)
                assertEquals(java.time.OffsetDateTime.parse(open).toInstant().toEpochMilli(), draft.beginsAtMillis)
                assertEquals(java.time.OffsetDateTime.parse(close).toInstant().toEpochMilli(), draft.endsAtMillis)
                assertEquals("Asia/Seoul", draft.timezone)
                assertTrue(draft.title.endsWith("[신청 기간]"))
            }
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun contestNormalizedMidnightRemainsExclusiveAndExactDeadlineRemainsInNotes() {
        val draft = applicationCalendarDraft(calendarNotice(NoticeApplication("10.07 24:00", opensOn = "2026-09-11", closesAt = "2026-10-08T00:00:00+09:00")))!!
        assertTrue(draft.allDay)
        assertEquals(millis("2026-09-11T00:00:00Z"), draft.beginsAtMillis)
        assertEquals(millis("2026-10-08T00:00:00Z"), draft.endsAtMillis)
        assertTrue(draft.description.contains("10.07 24:00"))
        assertTrue(draft.description.contains("2026-10-08T00:00:00+09:00"))
    }

    @Test fun dateRangesAreInclusiveWhileEndOnlyMidnightUsesPreviousDay() {
        val range = calendarPeriod(null, "2026-09-11", null, "2026-09-12", "Asia/Seoul", true)!!
        assertEquals(millis("2026-09-13T00:00:00Z"), range.end)
        val close = applicationCalendarDraft(calendarNotice(NoticeApplication("마감", closesAt = "2026-10-08T00:00:00+09:00")))!!
        assertEquals(millis("2026-10-07T00:00:00Z"), close.beginsAtMillis)
        assertEquals(millis("2026-10-08T00:00:00Z"), close.endsAtMillis)
        assertTrue(close.title.endsWith("[신청 마감]"))
        val nonMidnight = calendarPeriod(null, "2026-09-11", "2026-09-12T12:30:00+09:00", null, "Asia/Seoul", true)!!
        assertEquals(millis("2026-09-13T00:00:00Z"), nonMidnight.end)
    }

    @Test fun missingEndUsesKnownDayWithUnknownNoteAndNoDatesHaveNoAction() {
        val draft = applicationCalendarDraft(calendarNotice(NoticeApplication("시작 안내", opensAt = "2026-09-11T15:00:00+09:00")))!!
        assertTrue(draft.allDay)
        assertEquals(millis("2026-09-11T00:00:00Z"), draft.beginsAtMillis)
        assertEquals(millis("2026-09-12T00:00:00Z"), draft.endsAtMillis)
        assertTrue(draft.description.contains("마감: 미확인"))
        assertTrue(draft.description.contains("15:00:00+09:00"))
        assertNull(applicationCalendarDraft(calendarNotice()))
    }

    @Test fun malformedPresentOrReversedValuesCannotFallBackToOtherDates() {
        for (bad in listOf("", "2026-02-30", "2026-9-11", "2026-09-11garbage"))
            assertNull(calendarPeriod(null, bad, null, "2026-10-01", "Asia/Seoul", true))
        assertNull(calendarPeriod("2026-09-11T24:00:00+09:00", "2026-09-11", null, "2026-10-01", "Asia/Seoul", true))
        assertNull(calendarPeriod("2026-09-11T10:00:00", null, null, null, "Asia/Seoul", true))
        assertNull(calendarPeriod(null, "2026-10-11", null, "2026-10-10", "Asia/Seoul", true))
        assertNull(calendarPeriod("2026-09-11T15:00:00+09:00", null, "2026-09-11T14:00:00+09:00", null, "Asia/Seoul", true))
        assertNull(calendarPeriod("2026-09-11T15:00:00+09:00", null, "2026-09-11T15:00:00+09:00", null, "Asia/Seoul", true))
        assertNull(calendarPeriod(null, "2026-09-11", null, null, "Invalid/Zone", true))
        assertNull(calendarPeriod("2026-09-11T15:00:00+09:00", "2026-09-12", null, null, "Asia/Seoul", true))
    }

    @Test fun onlyVerifiedHttpApplicationUrlIsLabeledApplicationAndSourceStaysSeparate() {
        val application = NoticeApplication("안내", closesOn = "2026-09-16", url = "https://example.org/apply?q=%ED%95%9C&x=1#form")
        val valid = applicationCalendarDraft(calendarNotice(application))!!
        assertTrue(valid.description.contains("신청 URL: ${application.url}"))
        assertTrue(valid.description.contains("원문: https://example.org/source"))
        for (url in listOf(null, "javascript:alert(1)", "https:///missing", "https://user:pass@example.org/", "https://example.org/ bad")) {
            val draft = applicationCalendarDraft(calendarNotice(application.copy(url = url)))!!
            assertTrue(draft.description.contains("신청 URL: 미확인"))
            assertFalse(draft.description.contains("신청 URL: https://example.org/source"))
        }
    }
}
