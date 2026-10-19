package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.ScheduleItemPatch
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.ScheduleStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

// 세부 일정 입력(admin-v1.md "세부 일정 편집기" ScheduleItemInput). PATCH는 보낸 필드만 바꾼다(날짜·시각 네 필드는 하나라도 오면 그 묶음으로 교체)
data class ScheduleItemRequest(
    val title: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val timeZone: String? = null,
    val dateLabel: String? = null,
    val note: String? = null,
) {
    fun toPatch() = ScheduleItemPatch(
        title = title?.trim(),
        kind = kind?.let { value -> ScheduleKind.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("kind는 DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT 중 하나입니다") },
        status = status?.let { value -> ScheduleStatus.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("status는 TENTATIVE·CONFIRMED·CANCELLED 중 하나입니다") },
        hasTime = listOf(startAt, endAt, startDate, endDate).any { it != null },
        startAt = startAt?.trim()?.ifEmpty { null }?.let(::dateTime),
        endAt = endAt?.trim()?.ifEmpty { null }?.let(::dateTime),
        startDate = startDate?.trim()?.ifEmpty { null }?.let(::date),
        endDate = endDate?.trim()?.ifEmpty { null }?.let(::date),
        timeZone = timeZone?.trim()?.ifEmpty { null }?.let { try { ZoneId.of(it) } catch (_: Exception) { throw InvalidInputException("시간대가 올바르지 않습니다: $it") } },
        dateLabel = dateLabel?.trim(),
        note = note?.trim(),
    )

    private fun date(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("날짜 형식이 올바르지 않습니다: $value") }
    private fun dateTime(value: String): OffsetDateTime = try { OffsetDateTime.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("시각 형식이 올바르지 않습니다: $value") }
}
