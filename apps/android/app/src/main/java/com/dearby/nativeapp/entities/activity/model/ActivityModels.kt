package com.dearby.nativeapp.entities.activity.model

import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

/** A timed schedule with both ends; all-day and undated schedules stay in [ActivityModel.dateLabel]. */
data class ScheduleModel(val title: String, val startAt: String, val endAt: String, val timeZone: String = "Asia/Seoul")

/** Recruitment period from `application`; either end may be unknown. */
data class ApplicationWindow(val startAt: String?, val endAt: String?)

/** One activity from `GET /v1/activities` (contract "활동 조회"). [id] is the API's number as text. */
data class ActivityModel(
    val id: String, val name: String, val description: String, val organization: String, val program: String,
    val officialUrl: String, val imageUrl: String?, val application: ApplicationWindow?,
    val dateLabel: String, val address: String?, val schedules: List<ScheduleModel>,
) {
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

private const val OPEN = "모집 중"
private const val UNKNOWN = "모집 여부 확인 필요"

fun instant(raw: String?): Instant? = raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun safeHttpsUrl(raw: String?): String? = raw?.takeIf { value ->
    runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } == true &&
        value.none { it.isISOControl() }
}
