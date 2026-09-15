package io.fixabley.dearby.shared.ui

import org.junit.Assert.*
import org.junit.Test

class PeriodTextTest {
    private fun format(startAt: String? = null, startOn: String? = null, endAt: String? = null,
        endOn: String? = null, zone: String = "Asia/Seoul") =
        compactPeriodText(startAt, startOn, endAt, endOn, zone, "원래 안내 보존", "마감")

    @Test fun sameDayTimesUseOneDateAndKoreanWeekday() {
        assertEquals("2026.9.15(화) 14:00–16:00", format("2026-09-15T14:00:00+09:00", endAt = "2026-09-15T16:00:00+09:00"))
    }
    @Test fun yearBoundariesAndMissingTimesStayExplicit() {
        assertEquals("2026.12.31(목) · 시간 미확인 – 2027.1.1(금) · 시간 미확인", format(startOn = "2026-12-31", endOn = "2027-01-01"))
        assertEquals("2026.9.16(수) · 시간 미확인 마감", format(endOn = "2026-09-16"))
        assertEquals("2026.10.14(수) · 시간 미확인 시작 · 종료 미확인", format(startOn = "2026-10-14"))
    }
    @Test fun malformedConflictingAndReversedDatesNeverInventAPeriod() {
        assertEquals("원래 안내 보존", format(startAt = "invalid", startOn = "2026-09-15"))
        assertEquals("원래 안내 보존", format(startOn = "2026-02-30"))
        assertEquals("원래 안내 보존", format(startOn = "2026-10-02", endOn = "2026-10-01"))
        assertEquals("원래 안내 보존", format("2026-09-15T14:00:00+09:00", "2026-09-16"))
        assertEquals("원래 안내 보존", format(endAt = "2026-09-15T14:00:00+09:00", endOn = "2026-09-16"))
        assertEquals("원래 안내 보존", format())
    }
    @Test fun midnightAndSourceTimeZoneAreNotReinterpretedAsDeviceTime() {
        assertEquals("2026.9.17(목) 00:00 마감", format(endAt = "2026-09-17T00:00:00+09:00"))
        assertEquals("2026.9.15(화) 01:00 시작 · 종료 미확인 (America/New_York)",
            format("2026-09-15T14:00:00+09:00", zone = "America/New_York"))
    }
}
