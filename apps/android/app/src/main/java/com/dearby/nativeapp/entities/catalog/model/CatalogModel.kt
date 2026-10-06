package com.dearby.nativeapp.entities.catalog.model

import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

/** A timed schedule; date-only schedules stay in [ActivityModel.dateLabel]. */
data class ScheduleModel(val title: String, val startAt: String, val endAt: String, val timeZone: String = "Asia/Seoul")

/** One activity from `GET /v1/catalog` (contract catalog-v1). Missing values stay null and read as unconfirmed. */
data class ActivityModel(
    val id: String, val title: String, val summary: String, val organization: String?,
    val participation: String, val recruitmentStatus: String, val isRecruiting: Boolean, val freshness: String,
    // Raw ISO 8601 strings: a present but unreadable time must not count as absent.
    val recruitmentStartAt: String?, val recruitmentEndAt: String?, val sourceCheckedAt: String?, val validUntil: String?,
    val dateLabel: String, val location: String?, val cost: String?, val audience: String?, val roles: List<String>,
    val schedules: List<ScheduleModel>, val officialUrl: String, val applicationUrl: String?, val sourceNote: String,
) {
    /** Same rule as web discovery (`apps/web/src/lib/models.ts` `isRecruiting`). */
    fun isOpen(now: Instant): Boolean {
        if (!isRecruiting || recruitmentStatus != "open" || freshness != "verified") return false
        val checked = instant(sourceCheckedAt) ?: return false
        val valid = instant(validUntil) ?: return false
        if (checked > now || now >= valid) return false
        if (recruitmentStartAt != null && instant(recruitmentStartAt)?.let { it <= now } != true) return false
        if (recruitmentEndAt != null && instant(recruitmentEndAt)?.let { now < it } != true) return false
        return true
    }
    fun statusLabel(now: Instant) = if (isOpen(now)) "모집 중" else when (recruitmentStatus) {
        "scheduled" -> "모집 예정"
        "closed" -> "모집 마감"
        else -> "모집 여부 확인 필요"
    }
    /** The official application link, only while recruiting and only over https. */
    fun applyUrl(now: Instant) = if (isOpen(now)) safeHttpsUrl(applicationUrl) else null
    /** Quick apply on the discovery card: registration activities only. */
    fun quickApplyUrl(now: Instant) = if (participation == "registration") applyUrl(now) else null
    val recruitmentEnd get() = instant(recruitmentEndAt)
}

fun instant(raw: String?): Instant? = raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun safeHttpsUrl(raw: String?): String? = raw?.takeIf { value ->
    runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } == true &&
        value.none { it.isISOControl() }
}
