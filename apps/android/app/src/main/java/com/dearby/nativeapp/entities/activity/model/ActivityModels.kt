package com.dearby.nativeapp.entities.activity.model

import java.net.URI
import java.time.Instant
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

/** Recruitment period from `application`; either end may be unknown. */
data class ApplicationWindow(val startAt: String?, val endAt: String?)

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
) {
    /** Only schedules with both ends can be compared on a calendar. */
    val schedules get() = stageSchedules.mapNotNull { if (it.startAt == null || it.endAt == null) null else ScheduleModel(it.title, it.startAt, it.endAt, it.timeZone) }
    /**
     * 2026-10-14 rule: only `application` against [now]. No application or no times, or a time that cannot be
     * read, is unknown; a missing bound is open on that side.
     */
    fun statusLabel(now: Instant): String {
        val window = application ?: return UNKNOWN
        if (window.startAt == null && window.endAt == null) return UNKNOWN
        val start = window.startAt?.let { instant(it) ?: return UNKNOWN }
        val end = window.endAt?.let { instant(it) ?: return UNKNOWN }
        return when {
            start != null && now < start -> "모집 예정"
            end != null && now >= end -> "모집 마감"
            else -> OPEN
        }
    }
    fun isOpen(now: Instant) = statusLabel(now) == OPEN
    /** The official page, offered as the application link only while recruiting. */
    fun applyUrl(now: Instant) = if (isOpen(now)) safeHttpsUrl(officialUrl) else null
    val recruitmentEnd get() = instant(application?.endAt)
}

/** One page of `GET /v1/activities`; a null [nextCursor] is the last page. */
data class ActivityPage(val items: List<ActivityModel>, val nextCursor: String?)

/**
 * One program with open activities from `GET /v1/discovery/programs` (contract "발견 프로그램 묶음"). [id] is the API's number as
 * text; [nearestRecruitmentEndAt] is the earliest open deadline (ISO 8601) or null; [matchedActivityIds] are the open
 * activities whose name matched the search.
 */
data class ProgramSummary(
    val id: String, val name: String, val nameEn: String?, val organization: String, val openActivityCount: Int,
    val nearestRecruitmentEndAt: String?, val matchedActivityIds: List<String>,
)

/** One page of `GET /v1/discovery/programs`; [nextCursor] is the next integer offset as text, null on the last page. */
data class ProgramPage(val items: List<ProgramSummary>, val nextCursor: String?)

private const val OPEN = "모집 중"
private const val UNKNOWN = "모집 여부 확인 필요"

fun instant(raw: String?): Instant? = raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun safeHttpsUrl(raw: String?): String? = raw?.takeIf { value ->
    runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } == true &&
        value.none { it.isISOControl() }
}
