package com.dearby.nativeapp.entities.activity.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The RRULE subset schedules carry (contract "반복 일정과 겹침 결과 묶음", 2026-10-22): `FREQ=DAILY|WEEKLY`, `BYDAY`, `INTERVAL`
 * and one of `UNTIL` (a date, or a UTC instant) / `COUNT`. Device rules only need [describe], so `WKST` is tolerated; any other
 * key makes [parse] return null.
 */
data class Recurrence(val weekly: Boolean, val interval: Int, val days: Set<DayOfWeek>, val untilDay: LocalDate?, val untilInstant: Instant?, val count: Int?) {
    /**
     * Occurrence days from [first] (the first occurrence's local start, in [zone]), at most [limit]. WEEKLY without BYDAY repeats
     * [first]'s weekday; weeks run Monday to Sunday and every [interval]th week from [first]'s counts.
     */
    fun days(first: LocalDateTime, zone: ZoneId, limit: Int = MAX_OCCURRENCES): List<LocalDate> {
        val on = days.ifEmpty { if (weekly) setOf(first.dayOfWeek) else DayOfWeek.entries.toSet() }
        val firstWeek = first.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val max = minOf(limit, count ?: limit)
        return generateSequence(first.toLocalDate()) { it.plusDays(1) }
            // An open-ended device rule still stops: two years past the first day bounds the walk.
            .takeWhile { ChronoUnit.DAYS.between(first.toLocalDate(), it) <= 731 }
            .takeWhile { day -> untilDay?.let { day <= it } ?: untilInstant?.let { !day.atTime(first.toLocalTime()).atZone(zone).toInstant().isAfter(it) } ?: true }
            .filter { day ->
                day.dayOfWeek in on && if (weekly) ChronoUnit.WEEKS.between(firstWeek, day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) % interval == 0L
                else ChronoUnit.DAYS.between(first.toLocalDate(), day) % interval == 0L
            }
            .take(max).toList()
    }

    /** "평일", "매일", "매주 화·목", "2주마다 월" — without the time, which the caller adds. */
    fun describe(): String {
        val on = days.sorted()
        val named = when { on.isEmpty() || on.size == 7 -> null; on == WEEKDAYS -> "평일"; else -> on.joinToString("·") { KOREAN_DAYS[it.value - 1] } }
        return when {
            interval > 1 -> (if (weekly) "${interval}주마다" else "${interval}일마다") + (named?.let { " $it" } ?: "")
            named == "평일" -> named
            named != null -> "매주 $named"
            else -> "매일".takeUnless { weekly && on.isEmpty() } ?: "매주"
        }
    }

    companion object {
        const val MAX_OCCURRENCES = 400
        private val WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        private val KOREAN_DAYS = listOf("월", "화", "수", "목", "금", "토", "일")
        private val CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

        fun parse(raw: String?): Recurrence? = runCatching {
            val parts = raw!!.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
            if ((parts.keys - setOf("FREQ", "BYDAY", "INTERVAL", "UNTIL", "COUNT", "WKST")).isNotEmpty()) return null
            val weekly = when (parts["FREQ"]) { "WEEKLY" -> true; "DAILY" -> false; else -> return null }
            val days = parts["BYDAY"]?.split(',')?.map { DayOfWeek.of(CODES.indexOf(it.trim().uppercase()).also { i -> require(i >= 0) } + 1) }?.toSet().orEmpty()
            val until = parts["UNTIL"]
            Recurrence(weekly, parts["INTERVAL"]?.toInt()?.takeIf { it >= 1 } ?: 1, days,
                until?.takeIf { it.length == 8 }?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) },
                until?.takeIf { it.length > 8 }?.let { DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC")).parse(it, Instant::from) },
                parts["COUNT"]?.toInt()?.takeIf { it >= 1 })
        }.getOrNull()
    }
}
