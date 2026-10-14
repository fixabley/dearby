package io.wid.dearby.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityRecruitmentTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = OffsetDateTime.parse("2026-10-14T03:00:00Z")
    private val stamps = Timestamps(LocalDateTime.MIN, LocalDateTime.MIN)

    private fun schedule(slot: ScheduleKind, time: ScheduleTime?) =
        Schedule(1, "일정", slot, ScheduleStatus.CONFIRMED, time, "", null, null, 0, stamps)

    private fun activity(
        start: String? = null,
        end: String? = null,
        status: RecruitmentStatus = RecruitmentStatus.UNKNOWN,
        freshness: Freshness = Freshness.VERIFIED,
        checked: OffsetDateTime? = now.minusHours(1),
        validUntil: OffsetDateTime? = now.plusHours(23),
    ) = Activity(
        id = 1, programId = 1, name = "활동", description = "", contacts = emptyList(),
        participationType = ParticipationType.REGISTRATION, recruitmentStatus = status,
        location = null, cost = null, audience = null, roles = emptyList(),
        officialUrl = "https://example.com", applicationUrl = null,
        sourceCheckedAt = checked, validUntil = validUntil, freshness = freshness, sourceNote = "",
        application = if (start == null && end == null) null else schedule(
            ScheduleKind.DEADLINE,
            ScheduleTime.Timed(start?.let { OffsetDateTime.parse(it).atZoneSameInstant(seoul) }, end?.let { OffsetDateTime.parse(it).atZoneSameInstant(seoul) }),
        ),
        activitySchedule = schedule(ScheduleKind.EVENT, null),
        schedules = emptyList(),
        timestamps = stamps,
    )

    @Test
    fun `확인이 유효하고 모집 기간 안이면 모집 중`() {
        assertEquals(
            Recruitment(RecruitmentStatus.OPEN, Freshness.VERIFIED, true),
            activity(start = "2026-10-01T00:00:00Z", end = "2026-10-20T00:00:00Z").recruitmentAt(now),
        )
    }

    @Test
    fun `시작 전이면 모집 예정, 끝 시각부터는 마감`() {
        assertEquals(RecruitmentStatus.SCHEDULED, activity(start = "2026-10-15T00:00:00Z").recruitmentAt(now).status)
        assertEquals(RecruitmentStatus.CLOSED, activity(end = "2026-10-14T03:00:00Z").recruitmentAt(now).status)
        assertEquals(RecruitmentStatus.OPEN, activity(end = "2026-10-14T03:00:01Z").recruitmentAt(now).status)
    }

    @Test
    fun `운영자가 고른 마감은 기간보다 우선하고, 기간이 없으면 고른 상태를 쓴다`() {
        assertEquals(RecruitmentStatus.CLOSED, activity(end = "2026-10-20T00:00:00Z", status = RecruitmentStatus.CLOSED).recruitmentAt(now).status)
        assertEquals(RecruitmentStatus.SCHEDULED, activity(status = RecruitmentStatus.SCHEDULED).recruitmentAt(now).status)
    }

    @Test
    fun `확인이 지났거나 24시간을 넘게 유효하다고 적혀 있으면 오래된 정보로 보고 상태를 모른다`() {
        val expired = activity(end = "2026-10-20T00:00:00Z", validUntil = now).recruitmentAt(now)
        assertEquals(Recruitment(RecruitmentStatus.UNKNOWN, Freshness.STALE, false), expired)
        val tooLong = activity(end = "2026-10-20T00:00:00Z", validUntil = now.plusHours(24)).recruitmentAt(now)
        assertEquals(Freshness.STALE, tooLong.freshness)
        val failed = activity(freshness = Freshness.UNAVAILABLE).recruitmentAt(now)
        assertEquals(Freshness.UNAVAILABLE, failed.freshness)
    }
}
