package com.dearby.nativeapp.entities.activity.model

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.OffsetDateTime

/** A timed schedule with both ends, as the calendar comparison needs it; the full list is [ActivityModel.stageSchedules]. */
data class ScheduleModel(val title: String, val startAt: String, val endAt: String, val timeZone: String = "Asia/Seoul")

/**
 * One schedule as `GET /v1/activities` sends it (contract "활동 조회"): timed ([startAt] and/or [endAt]), all-day
 * ([startDate]/[endDateExclusive]) or undated ([dateLabel] only). [kind] is DEADLINE, ANNOUNCEMENT, INTERVIEW or EVENT.
 */
data class StageSchedule(
    val title: String, val kind: String, val startAt: String?, val endAt: String?, val timeZone: String,
    val startDate: String?, val endDateExclusive: String?, val dateLabel: String,
)

/**
 * Recruitment period from `application`: timed ([startAt]/[endAt]) or all-day ([startDate]/[endDateExclusive], Seoul days);
 * either end may be unknown.
 */
data class ApplicationWindow(val startAt: String?, val endAt: String?, val startDate: String? = null, val endDateExclusive: String? = null)

/** A structured venue (contract "활동 장소"): all four fields come together or the activity has no place. */
data class Place(val name: String, val address: String, val lat: Double, val lng: Double)

/** One activity from `GET /v1/activities` (contract "활동 조회"). [id] is the API's number as text. */
data class ActivityModel(
    val id: String, val name: String, val description: String, val organization: String, val program: String,
    val officialUrl: String, val imageUrl: String?, val application: ApplicationWindow?,
    val dateLabel: String, val address: String?,
    /** Every non-slot schedule in API order, for the progress track (contract "내 활동 진행 트랙"). */
    val stageSchedules: List<StageSchedule>,
    /** The ACTIVITY slot (activity period), the last stage of the track; null only when the API omitted it. */
    val activitySchedule: StageSchedule?,
    /** 대상자 조건; null when the activity has none or the JSON could not be read. */
    val eligibility: EligibilityGroup? = null,
    /** 활동 종류; null = 미분류 (contract "활동 종류(category)"). */
    val category: ActivityCategory? = null,
    /** 얻을 수 있는 것 (contract "태그"), dictionary order; empty when the API sent none. */
    val tags: List<Tag> = emptyList(),
    /** The collected venue phrase (`location`), already the place name when a [place] is set (contract "활동 장소"). */
    val location: String? = null,
    /** The curated venue with coordinates, or null (contract "활동 장소"). */
    val place: Place? = null,
    /** The operator's application link (contract "신청 확인 조건 완화·공개 applicationUrl"), or null; https only when used. */
    val applicationUrl: String? = null,
) {
    /** The venue name to show: the curated place, else the collected phrase, else the schedule address. */
    val venue: String? get() = place?.name ?: location ?: address
    /** Only schedules with both ends can be compared on a calendar. */
    val schedules get() = stageSchedules.mapNotNull { if (it.startAt == null || it.endAt == null) null else ScheduleModel(it.title, it.startAt, it.endAt, it.timeZone) }
    /**
     * Only `application` against [now] (2026-10-14 rule; 2026-10-19 "열린 활동 판정 일원화" adds date-only windows). A timed
     * window compares instants, an all-day window compares Seoul dates (`startDate ≤ today < endDateExclusive`); a missing
     * bound is open on that side. No application, or a value that cannot be read, is unknown.
     */
    fun statusLabel(now: Instant): String {
        val window = application ?: return UNKNOWN
        if (window.startAt != null || window.endAt != null) {
            val start = window.startAt?.let { instant(it) ?: return UNKNOWN }
            val end = window.endAt?.let { instant(it) ?: return UNKNOWN }
            return when {
                start != null && now < start -> "모집 예정"
                end != null && now >= end -> "모집 마감"
                else -> OPEN
            }
        }
        if (window.startDate == null && window.endDateExclusive == null) return UNKNOWN
        val today = now.atZone(SEOUL).toLocalDate()
        val start = window.startDate?.let { date(it) ?: return UNKNOWN }
        val endExclusive = window.endDateExclusive?.let { date(it) ?: return UNKNOWN }
        return when {
            start != null && today < start -> "모집 예정"
            endExclusive != null && today >= endExclusive -> "모집 마감"
            else -> OPEN
        }
    }
    /**
     * Applying is over (2026-10-19 밤 "신청 확인 조건 완화", same rule as iOS): the recruitment deadline passed, or, with no window to
     * judge by, the activity itself ended (its end, else its start, is behind [now]). Open, upcoming and unknown-but-not-ended still ask.
     */
    fun isClosed(now: Instant) = when (statusLabel(now)) {
        CLOSED -> true
        UNKNOWN -> (activityEnd ?: activityStart)?.let { it < now } == true
        else -> false
    }
    /** The application link for the detail CTA — [applicationUrl], else the official page — unless recruitment is closed. */
    fun applyUrl(now: Instant) = if (isClosed(now)) null else safeHttpsUrl(applicationUrl) ?: safeHttpsUrl(officialUrl)
    val recruitmentEnd get() = instant(application?.endAt)
    /**
     * Ordering key for discovery groups (contract "활동 종류", 2026-10-19): the recruitment deadline, or the activity start
     * (ACTIVITY slot `startAt`, else `startDate` at Seoul midnight) when the activity has no deadline; null sorts last.
     */
    val sortKey: Instant? get() = recruitmentEnd ?: activityStart
    /**
     * When the activity becomes relevant next (contract "모집 중인 프로그램만 보기", 2026-10-19 보완): the recruitment start
     * (`startAt`, else `startDate` at Seoul midnight), or the activity start when there is no recruitment window; null when unknown.
     */
    val upcomingKey: Instant? get() = application?.let { window ->
        instant(window.startAt) ?: window.startDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }
    } ?: activityStart
    /** The ACTIVITY slot's start: `startAt`, else `startDate` at Seoul midnight; null when the period is undated. */
    val activityStart: Instant? get() = instant(activitySchedule?.startAt)
        ?: activitySchedule?.startDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }
    /** The ACTIVITY slot's end: `endAt`, else `endDateExclusive` at Seoul midnight; null when the period is undated. */
    val activityEnd: Instant? get() = instant(activitySchedule?.endAt)
        ?: activitySchedule?.endDateExclusive?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

/** One page of `GET /v1/activities`; a null [nextCursor] is the last page. */
data class ActivityPage(val items: List<ActivityModel>, val nextCursor: String?)

/**
 * One program with open activities from `GET /v1/discovery/programs` (contract "발견 프로그램 묶음"). [id] is the API's number as
 * text; [nearestRecruitmentEndAt] is the earliest open deadline (ISO 8601) or null; [matchedActivityIds] are the open
 * activities whose name matched the search; [latestActivityAt] is the most recent activity start of its published
 * activities (contract "마감 숨기기"), and [openActivityCount] may be 0 when the program only has upcoming or past activities.
 * [upcomingActivityCount] counts 모집 예정 activities and [nearestRecruitmentStartAt] is their earliest recruitment (or activity)
 * start (2026-10-19 밤 "마감 숨기기" 정정: the switch hides only closed activities).
 */
data class ProgramSummary(
    val id: String, val name: String, val nameEn: String?, val organization: String, val openActivityCount: Int,
    val nearestRecruitmentEndAt: String?, val matchedActivityIds: List<String>, val latestActivityAt: String? = null,
    val upcomingActivityCount: Int = 0, val nearestRecruitmentStartAt: String? = null,
    /** Program tags, or the union of its open activities' tags (contract "태그", at most 6). */
    val tags: List<Tag> = emptyList(),
    /** The program logo as an absolute URL (contract "프로그램 로고": `/v1/programs/{id}/logo?v=` on the API origin), or null when it has none. */
    val logoUrl: String? = null,
)

/** One page of `GET /v1/discovery/programs`; [nextCursor] is the next integer offset as text, null on the last page. */
data class ProgramPage(val items: List<ProgramSummary>, val nextCursor: String?)

private const val OPEN = "모집 중"
private const val CLOSED = "모집 마감"
private const val UNKNOWN = "모집 여부 확인 필요"

fun instant(raw: String?): Instant? = raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun safeHttpsUrl(raw: String?): String? = raw?.takeIf { value ->
    runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } == true &&
        value.none { it.isISOControl() }
}
