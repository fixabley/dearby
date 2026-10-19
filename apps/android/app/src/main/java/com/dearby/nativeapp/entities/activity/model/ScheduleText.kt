package com.dearby.nativeapp.entities.activity.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
private val dayText = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val dayWithYear = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val timeText = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

/**
 * A schedule as text (contract "일정 표시 우선순위", 2026-10-19 밤): the structured dates when there are any — timed
 * `10월 20일 (화) 14:00 ~ 15:00` (a second day spelled out), all-day `10월 20일 (화) ~ 10월 22일 (목)` with the exclusive end moved back a
 * day, the year only on a date outside [today]'s year (`12월 30일 (수) ~ 2027년 1월 2일 (토)`); only the end known reads `~ … 마감` for a [recruitment] window and `~ …까지` for an
 * activity period or a detail schedule — and the collected [dateLabel] only when there are none. Blank when neither exists; the
 * caller supplies `미정`/`일정 미확인`.
 */
fun scheduleText(startAt: String?, endAt: String?, startDate: String?, endDateExclusive: String?, timeZone: String = "Asia/Seoul", dateLabel: String = "", recruitment: Boolean = false, today: LocalDate = LocalDate.now(SEOUL)): String {
    val endOnly = if (recruitment) " 마감" else "까지"
    val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(SEOUL)
    val start = instant(startAt)
    val end = instant(endAt)
    val startDay = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val endDay = endDateExclusive?.let { runCatching { LocalDate.parse(it).minusDays(1) }.getOrNull() }
    fun day(date: LocalDate) = (if (date.year == today.year) dayText else dayWithYear).format(date)
    fun moment(at: Instant) = at.atZone(zone).let { day(it.toLocalDate()) + " " + timeText.format(it) }
    if (start != null || end != null) return when {
        start == null -> "~ " + moment(end!!) + endOnly
        end == null -> moment(start)
        start.atZone(zone).toLocalDate() == end.atZone(zone).toLocalDate() -> moment(start) + " ~ " + timeText.format(end.atZone(zone))
        else -> moment(start) + " ~ " + moment(end)
    }
    return when {
        startDay == null && endDay == null -> dateLabel.trim()
        startDay == null -> "~ " + day(endDay!!) + endOnly
        endDay == null || endDay <= startDay -> day(startDay)
        else -> day(startDay) + " ~ " + day(endDay)
    }
}

/** This schedule's text by the rule above. */
fun StageSchedule.scheduleText() = scheduleText(startAt, endAt, startDate, endDateExclusive, timeZone, dateLabel)
