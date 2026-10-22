package io.wid.dearby.batch

import io.wid.dearby.batch.application.ActivityVerifier
import io.wid.dearby.application.geocode.GeocodeFailedException
import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import io.wid.dearby.batch.application.ExtractedActivity
import io.wid.dearby.batch.application.ExtractedPlace
import io.wid.dearby.batch.application.PlaceQuery
import io.wid.dearby.batch.application.PlaceResolver
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

    @Test
    fun `장소는 온라인이 아니고 근거 구절이 원문에 있고 그 안에 이름이 있을 때만 검색어가 되고, 주소는 원문에 있을 때만 붙는다`() {
        val venue = page.copy(text = page.text + " 장소: 코엑스 그랜드볼룸 (서울 강남구 영동대로 513)")
        fun place(name: String?, address: String? = null, online: Boolean = false, quote: String? = "장소: 코엑스 그랜드볼룸") =
            ActivityVerifier.place(candidate().copy(place = ExtractedPlace(name, address, online, quote)), venue)
        assertEquals(PlaceQuery("코엑스 그랜드볼룸", "서울 강남구 영동대로 513"), place("코엑스 그랜드볼룸", "서울 강남구 영동대로 513"))
        assertEquals(PlaceQuery("코엑스 그랜드볼룸", null), place("코엑스 그랜드볼룸", "서울 송파구 올림픽로 300"))
        assertNull(place(null, online = true, quote = null))
        assertNull(place("코엑스 그랜드볼룸", quote = "장소: 잠실 롯데타워")) // 원문에 없는 구절
        assertNull(place("벡스코", quote = "장소: 코엑스 그랜드볼룸")) // 구절에 이름이 없음
        assertNull(ActivityVerifier.place(candidate(), venue))
    }

    @Test
    fun `장소 지오코딩은 주소 결과가 하나이거나, 키워드 첫 결과 이름이 제출 이름을 포함하고 시군구가 같을 때만 저장한다`() {
        val coex = GeocodeResult("코엑스 그랜드볼룸", "서울 강남구 영동대로 513", 37.51, 127.06, "fake")
        fun resolver(address: List<GeocodeResult> = emptyList(), keyword: List<GeocodeResult> = emptyList(), fail: Boolean = false) = PlaceResolver(object : Geocoder {
            override fun search(query: String) = address + keyword
            override fun searchAddress(query: String) = if (fail) throw GeocodeFailedException("down") else address
            override fun searchKeyword(query: String) = if (fail) throw GeocodeFailedException("down") else keyword
        })
        // 주소 결과가 정확히 하나: 이름은 제출한 것, 주소·좌표는 제공자 값
        val byAddress = resolver(address = listOf(coex.copy(name = "코엑스"))).resolve(PlaceQuery("코엑스 그랜드볼룸", "서울특별시 강남구 영동대로 513"))!!
        assertEquals(listOf("코엑스 그랜드볼룸", "서울 강남구 영동대로 513"), listOf(byAddress.addressLine1, byAddress.addressLine2))
        // 주소 결과가 여럿이면 키워드로: 이름 포함 + 시군구 같음이면 저장, 시군구가 다르거나 이름이 다르면 버린다
        val two = listOf(coex, coex.copy(address = "서울 강남구 영동대로 511"))
        assertEquals(37.51, resolver(address = two, keyword = listOf(coex)).resolve(PlaceQuery("코엑스그랜드볼룸", "서울 강남구 영동대로 513"))?.lat)
        assertNull(resolver(keyword = listOf(coex)).resolve(PlaceQuery("코엑스 그랜드볼룸", "서울 송파구 올림픽로 300")))
        assertNull(resolver(keyword = listOf(coex.copy(name = "코엑스몰"))).resolve(PlaceQuery("코엑스 그랜드볼룸", null)))
        assertEquals("코엑스 그랜드볼룸", resolver(keyword = listOf(coex)).resolve(PlaceQuery("코엑스 그랜드볼룸", null))?.addressLine1)
        assertNull(resolver(fail = true).resolve(PlaceQuery("코엑스 그랜드볼룸", "서울 강남구 영동대로 513")))
        assertEquals(listOf("강남구", "포항시", null), listOf("서울특별시 강남구 영동대로", "경상북도 포항시 남구 지곡로 80", "온라인").map { PlaceResolver.district(it) })
    }
}
