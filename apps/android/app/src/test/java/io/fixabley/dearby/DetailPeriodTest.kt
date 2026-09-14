package io.fixabley.dearby

import io.fixabley.dearby.pages.noticedetail.model.*
import org.junit.Assert.*
import org.junit.Test

class DetailPeriodTest {
    @Test fun sameDaySeparatesDateFromTimeAndCrossYearLabelsBothEnds() {
        val same = detailPeriod("2026-09-15T14:00:00+09:00", null, "2026-09-15T16:00:00+09:00", null, "Asia/Seoul", "raw")
        assertEquals(listOf(DetailPeriodLine("2026년 9월 15일 (화)", "오후 2시부터 오후 4시까지")), same.lines)
        val cross = detailPeriod(null, "2026-12-31", null, "2027-01-01", "Asia/Seoul", "raw")
        assertEquals("2026년 12월 31일 (목)", cross.lines.first().date)
        assertEquals("2027년 1월 1일 (금)", cross.lines.last().date)
        assertTrue(cross.lines.all { it.time.endsWith("시간 미확인") })
    }
    @Test fun invalidConflictReversalPreservesAllRawFields() {
        val conflict = detailPeriod("2026-09-15T14:00:00+09:00", "2026-09-16", null, null, "Asia/Seoul", "원문 충돌")
        assertFalse(conflict.valid)
        assertEquals("원문 충돌", conflict.lines.single().date)
        assertTrue(conflict.lines.single().time.contains("2026-09-16"))
        assertTrue(conflict.lines.single().time.contains("14:00:00+09:00"))
        assertFalse(detailPeriod(null, "invalid", null, null, "Asia/Seoul", "raw").valid)
        assertFalse(detailPeriod(null, "2026-09-16", null, "2026-09-15", "Asia/Seoul", "raw").valid)
    }
    @Test fun timezoneUnknownEndAndOnlyExactSummaryDuplicateSuppression() {
        val d = detailPeriod("2026-09-15T14:00:00+09:00", null, null, null, "America/New_York", "raw")
        assertEquals("America/New_York", d.timezone)
        assertEquals("오전 1시부터", d.lines.first().time)
        assertEquals("종료 미확인", d.lines.last().date)
        assertNull(applicationPeriodNote("2026-09-15 13:00 마감", "2026-09-15T13:00:00+09:00", d))
        assertEquals("2026.09.14 24:00 마감", applicationPeriodNote("2026.09.14 24:00 마감", "2026-09-15T00:00:00+09:00", d))
        assertEquals("마감 시각 미기재", applicationPeriodNote("마감 시각 미기재", null, d))
    }
    @Test fun secondsMixedPrecisionAndMissingStartStayExplicit() {
        val seconds = detailPeriod("2026-09-15T14:00:30.123+09:00", null,
            "2026-09-15T16:00:45+09:00", null, "Asia/Seoul", "raw")
        assertEquals("오후 2시 30.123초부터 오후 4시 45초까지", seconds.lines.single().time)
        val mixed = detailPeriod(null, "2026-09-15", "2026-09-15T16:00:00+09:00", null, "Asia/Seoul", "raw")
        assertEquals("시작 시간 미확인 · 종료 오후 4시", mixed.lines.single().time)
        val endOnly = detailPeriod(null, null, null, "2026-09-15", "Asia/Seoul", "raw", "마감")
        assertEquals("시작 미확인", endOnly.lines.first().date)
        assertEquals("2026년 9월 15일 (화)", endOnly.lines.last().date)
    }
}
