package io.wid.dearby.batch

import io.wid.dearby.batch.application.ActivityVerifier
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.ExtractedRecurrence
import io.wid.dearby.batch.application.ExtractedSchedule
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.Verification
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ActivityVerifierTest {

    private val page = FetchedPage(
        url = "https://letswift.kr/2026/",
        text = "Let'Swift 2026 워크숍 **참가 신청**은 10월 1일부터   10월 7일까지 받습니다.",
        links = setOf("https://ticketa.co/e/apply"),
    )
    private val now = OffsetDateTime.parse("2026-10-15T00:00:00Z")

    private fun candidate(
        quote: String = "참가 신청은 10월 1일부터 10월 7일까지 받습니다",
        applicationUrl: String? = "https://ticketa.co/e/apply",
        start: String? = "2026-10-01T14:07:00+09:00",
        end: String? = "2026-10-08T00:00:00+09:00",
    ) = ExtractedActivity(
        roundKey = "2026-워크숍", name = "워크숍", nameEn = " Workshop ", description = "", descriptionEn = " ", participationType = "REGISTRATION",
        applicationUrl = applicationUrl, recruitmentStartAt = start, recruitmentEndAt = end, dateLabel = "2026-10-08",
        schedules = emptyList(), location = null, cost = null, audience = null, evidenceQuote = quote,
    )

    @Test
    fun `근거 구절은 공백·마크다운 차이를 무시하고 원문에서 찾는다`() {
        val result = assertIs<Verification.Accepted>(ActivityVerifier.verify(candidate(), page, true, now))
        // 영문 번역도 앞뒤 공백을 지우고, 빈 값은 없는 것으로 본다
        assertEquals("Workshop", result.activity.nameEn)
        assertNull(result.activity.descriptionEn)
        assertEquals("https://letswift.kr/2026/", result.activity.officialUrl)
        assertEquals(OffsetDateTime.parse("2026-10-07T15:00:00Z").toInstant(), result.activity.recruitmentEndAt!!.toInstant())
    }

    @Test
    fun `원문에 없는 구절, 너무 짧은 구절, 끝이 시작보다 앞선 기간은 뺀다`() {
        assertIs<Verification.Rejected>(ActivityVerifier.verify(candidate(quote = "참가비는 무료입니다. 누구나 오세요"), page, true, now))
        assertIs<Verification.Rejected>(ActivityVerifier.verify(candidate(quote = "신청"), page, true, now))
        assertIs<Verification.Rejected>(ActivityVerifier.verify(candidate(start = "2026-10-09T00:00:00+09:00"), page, true, now))
    }

    @Test
    fun `신청 주소는 원문 페이지의 링크일 때만 남기고, 읽을 수 없는 시각과 틀리거나 맞지 않는 반복 규칙은 그 필드만 버린다`() {
        val result = assertIs<Verification.Accepted>(
            ActivityVerifier.verify(candidate(applicationUrl = "https://elsewhere.example/apply", start = "10월 1일"), page, true, now)
        )
        assertNull(result.activity.applicationUrl)
        assertNull(result.activity.recruitmentStartAt)
        // 반복(native-v1.md "반복 일정과 겹침 결과 묶음"): 시각 세부 일정과 날짜 + 회차 시각이 있는 활동 기간에만 남고 정규화된다
        fun session(rule: String?, startAt: String? = "2026-11-03T19:00:00+09:00") =
            ExtractedSchedule("정기 세션", "EVENT", startAt, startAt?.replace("19:00", "21:00"), "매주 화 19:00", if (startAt == null) "2026-11-03" else null, null, rule)
        val repeating = assertIs<Verification.Accepted>(
            ActivityVerifier.verify(
                candidate().copy(
                    startDate = "2027-03-02", endDate = "2027-08-31",
                    activityRecurrence = ExtractedRecurrence("FREQ=WEEKLY;UNTIL=20270831;BYDAY=FR,MO,TU,WE,TH", "09:00", "17:00"),
                    schedules = listOf(session("freq=weekly;byday=tu;count=8"), session("FREQ=MONTHLY;COUNT=8"), session("FREQ=WEEKLY;COUNT=8", startAt = null)),
                ),
                page, true, now,
            )
        ).activity
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270831", repeating.activityRecurrence?.rule?.rule)
        assertEquals("09:00-17:00", repeating.activityRecurrence?.time?.let { "${it.startTime}-${it.endTime}" })
        assertEquals(listOf("FREQ=WEEKLY;BYDAY=TU;COUNT=8", null, null), repeating.schedules.map { it.recurrence?.rule })
        // 회차 시각이 틀리거나 활동 기간 날짜가 없으면 활동 기간 반복은 버린다
        listOf(candidate().copy(startDate = "2027-03-02", activityRecurrence = ExtractedRecurrence("FREQ=DAILY;COUNT=5", "17:00", "09:00")),
            candidate().copy(activityRecurrence = ExtractedRecurrence("FREQ=DAILY;COUNT=5", "09:00", "17:00")))
            .forEach { assertNull(assertIs<Verification.Accepted>(ActivityVerifier.verify(it, page, true, now)).activity.activityRecurrence) }
    }
}
