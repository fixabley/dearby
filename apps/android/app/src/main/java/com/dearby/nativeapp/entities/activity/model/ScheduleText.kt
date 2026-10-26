package com.dearby.nativeapp.entities.activity.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

private val shortDay = DateTimeFormatter.ofPattern("M.d", Locale.KOREAN)
private val shortDayWithYear = DateTimeFormatter.ofPattern("yyyy.M.d", Locale.KOREAN)

/** The recruitment start's Seoul day (`startAt`, else `startDate`); null when the window has no start. */
private fun ApplicationWindow.firstDay(): LocalDate? = instant(startAt)?.atZone(SEOUL)?.toLocalDate() ?: startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** The recruitment window's first and last Seoul day and the deadline time (null for an all-day end); null unless both ends are known. */
private fun ApplicationWindow.days(): Triple<LocalDate, LocalDate, Instant?>? {
    val end = instant(endAt)
    val first = firstDay() ?: return null
    val last = end?.atZone(SEOUL)?.toLocalDate() ?: endDateExclusive?.let { runCatching { LocalDate.parse(it).minusDays(1) }.getOrNull() } ?: return null
    return Triple(first, last, end)
}

/**
 * 모집 날짜 표기 (user 2026-10-26): a window with both ends reads `10.1 – 10.20` (`yyyy.` on a day outside [today]'s year), with the deadline
 * time on [withTime] (`10.1 – 10.20 18:00`); null when either end is unknown, where the caller keeps [scheduleText] (`~ 10월 20일 (화) 마감`).
 */
fun ApplicationWindow.rangeText(withTime: Boolean = false, today: LocalDate = LocalDate.now(SEOUL)): String? {
    val (first, last, end) = days() ?: return null
    fun day(date: LocalDate) = (if (date.year == today.year) shortDay else shortDayWithYear).format(date)
    return day(first) + " – " + day(last) + (end?.takeIf { withTime }?.let { " " + timeText.format(it.atZone(SEOUL)) } ?: "")
}

/** `D-3` or `D-DAY` to the deadline's Seoul day for a window with both ends (the badge beside [rangeText]); null otherwise or once it passed. */
fun ApplicationWindow.deadlineCount(today: LocalDate = LocalDate.now(SEOUL)): String? {
    val left = ChronoUnit.DAYS.between(today, days()?.second ?: return null)
    return when { left > 0 -> "D-$left"; left == 0L -> "D-DAY"; else -> null }
}

/**
 * `시작 D-5` or `오늘 시작` to the recruitment start's Seoul day while 모집 예정 (user 2026-10-26 "모집 예정 활동에 시작 D-5 배지"), the badge
 * beside the status chip; null without a start (the chip alone) or once it began.
 */
fun ApplicationWindow.startCount(today: LocalDate = LocalDate.now(SEOUL)): String? {
    val left = ChronoUnit.DAYS.between(today, firstDay() ?: return null)
    return when { left > 0 -> "시작 D-$left"; left == 0L -> "오늘 시작"; else -> null }
}
