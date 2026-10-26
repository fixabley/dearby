package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.activity.ScheduleItemPatch
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.Recurrence
import tools.jackson.databind.JsonNode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.SchedulePhase
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
    val phase: String? = null, // SELECTION|ACTIVITY(native-v1.md "선발 과정·활동 일정 분리"). 생략은 유지(만들 때는 서버 규칙)
    val startAt: String? = null,
    val endAt: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val timeZone: String? = null,
    val dateLabel: String? = null,
    val note: String? = null,
    val overlapCheck: JsonNode? = null, // 겹침 확인 시간 {startTime, endTime}(HH:mm, 서울). 생략은 유지, JSON null은 지운다
    val recurrence: JsonNode? = null, // 반복 규칙(RRULE 본문). 생략은 유지, JSON null은 지운다
) {
    fun toPatch() = ScheduleItemPatch(
        title = title?.trim(),
        kind = kind?.let { value -> ScheduleKind.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("kind는 DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT 중 하나입니다") },
        status = status?.let { value -> ScheduleStatus.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("status는 TENTATIVE·CONFIRMED·CANCELLED 중 하나입니다") },
        phase = phase?.let { value -> SchedulePhase.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: throw InvalidInputException("phase는 SELECTION·ACTIVITY 중 하나입니다") },
        hasTime = listOf(startAt, endAt, startDate, endDate).any { it != null },
        startAt = startAt?.trim()?.ifEmpty { null }?.let(::dateTime),
        endAt = endAt?.trim()?.ifEmpty { null }?.let(::dateTime),
        startDate = startDate?.trim()?.ifEmpty { null }?.let(::date),
        endDate = endDate?.trim()?.ifEmpty { null }?.let(::date),
        timeZone = timeZone?.trim()?.ifEmpty { null }?.let { try { ZoneId.of(it) } catch (_: Exception) { throw InvalidInputException("시간대가 올바르지 않습니다: $it") } },
        dateLabel = dateLabel?.trim(),
        note = note?.trim(),
        hasOverlapCheck = overlapCheck != null,
        overlapCheck = overlapCheck?.takeUnless { it.isNull }?.let(::parseOverlapCheck),
        hasRecurrence = recurrence != null,
        recurrence = recurrence?.takeUnless { it.isNull }?.let(::parseRecurrence),
    )

    private fun date(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("날짜 형식이 올바르지 않습니다: $value") }
    private fun dateTime(value: String): OffsetDateTime = try { OffsetDateTime.parse(value) } catch (_: DateTimeParseException) { throw InvalidInputException("시각 형식이 올바르지 않습니다: $value") }
}

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// 겹침 확인 시간 {startTime, endTime}(HH:mm, 서울). 세부 일정 입력과 활동의 모집·활동 기간 객체가 함께 쓴다
internal fun parseOverlapCheck(node: JsonNode): OverlapCheck {
    if (!node.isObject) throw InvalidInputException("겹침 확인 시간은 객체 또는 null이어야 합니다")
    fun time(field: String) = node.path(field).takeIf { it.isTextual }?.asString()?.trim()?.let {
        try { LocalTime.parse(it, HH_MM) } catch (_: DateTimeParseException) { null }
    } ?: throw InvalidInputException("겹침 확인 시간의 ${field}는 HH:mm 형식이어야 합니다")
    return OverlapCheck(time("startTime"), time("endTime"))
}

// 반복 규칙(RRULE 본문 문자열). 세부 일정 입력과 활동의 모집·활동 기간 객체가 함께 쓴다
internal fun parseRecurrence(node: JsonNode): Recurrence =
    Recurrence.parse(node.takeIf { it.isTextual }?.asString() ?: throw InvalidInputException("반복 규칙은 문자열 또는 null이어야 합니다"))
