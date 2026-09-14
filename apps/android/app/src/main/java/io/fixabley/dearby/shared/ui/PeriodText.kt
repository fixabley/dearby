package io.fixabley.dearby.shared.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure presentation; never changes calendar export boundaries or guesses missing times. */
internal fun compactPeriodText(startAt: String?, startOn: String?, endAt: String?, endOn: String?,
    timezone: String, fallback: String, endLabel: String = "종료"): String = try {
    val zone = ZoneId.of(timezone)
    fun date(raw: String): LocalDate {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw))
        return LocalDate.parse(raw)
    }
    val startTime = startAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val endTime = endAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val startDate = startOn?.let(::date)
    val endDate = endOn?.let(::date)
    require(startTime == null || startDate == null || startTime.toLocalDate() == startDate)
    require(endTime == null || endDate == null || endTime.toLocalDate() == endDate)
    val start = startTime?.toLocalDate() ?: startDate
    val end = endTime?.toLocalDate() ?: endDate
    require(start != null || end != null)
    require(start == null || end == null || !end.isBefore(start))
    require(startTime == null || endTime == null || endTime.toInstant() > startTime.toInstant())
    val day = DateTimeFormatter.ofPattern("uuuu.M.d(E)", Locale.KOREAN)
    val shortDay = DateTimeFormatter.ofPattern("M.d(E)", Locale.KOREAN)
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    val startText = start?.format(day)?.plus(startTime?.let { " ${it.format(time)}" } ?: " · 시간 미확인")
    val endText = end?.format(if (start?.year == end.year) shortDay else day)
        ?.plus(endTime?.let { " ${it.format(time)}" } ?: " · 시간 미확인")
    val text = when {
        start == null -> "$endText $endLabel"
        end == null -> "$startText 시작 · 종료 미확인"
        startTime != null && endTime != null && start == end -> "${start.format(day)} ${startTime.format(time)}–${endTime.format(time)}"
        start == end && startTime == null && endTime == null -> "$startText"
        else -> "$startText – $endText"
    }
    text + if (timezone == "Asia/Seoul") "" else " ($timezone)"
} catch (_: IllegalArgumentException) { fallback } catch (_: java.time.DateTimeException) { fallback }
