package io.wid.dearby.batch

import io.wid.dearby.batch.application.ActivityVerifier
import io.wid.dearby.batch.application.ExtractedActivity
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
        roundKey = "2026-워크숍", name = "워크숍", description = "", participationType = "REGISTRATION",
        applicationUrl = applicationUrl, recruitmentStartAt = start, recruitmentEndAt = end, dateLabel = "2026-10-08",
        schedules = emptyList(), location = null, cost = null, audience = null, evidenceQuote = quote,
    )

    @Test
    fun `근거 구절은 공백·마크다운 차이를 무시하고 원문에서 찾는다`() {
        val result = assertIs<Verification.Accepted>(ActivityVerifier.verify(candidate(), page, true, now))
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
    fun `신청 주소는 원문 페이지의 링크일 때만 남기고, 읽을 수 없는 시각은 비운다`() {
        val result = assertIs<Verification.Accepted>(
            ActivityVerifier.verify(candidate(applicationUrl = "https://elsewhere.example/apply", start = "10월 1일"), page, true, now)
        )
        assertNull(result.activity.applicationUrl)
        assertNull(result.activity.recruitmentStartAt)
    }
}
