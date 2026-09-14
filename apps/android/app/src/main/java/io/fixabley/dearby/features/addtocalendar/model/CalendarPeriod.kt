package io.fixabley.dearby.features.addtocalendar.model

import java.time.*

internal data class CalendarPeriod(val begin: Long, val end: Long, val allDay: Boolean, val endOnly: Boolean)

/** Strict source dates; date-only events use UTC midnight boundaries, never the device zone. */
internal fun calendarPeriod(startAt: String?, startOn: String?, endAt: String?, endOn: String?, timezone: String, allowEndOnly: Boolean): CalendarPeriod? = try {
    val zone = ZoneId.of(timezone)
    fun date(value: String): LocalDate {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))
        return LocalDate.parse(value)
    }
    val startTime = startAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val endTime = endAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val startDate = startOn?.let(::date)
    val endDate = endOn?.let(::date)
    require(startTime == null || startDate == null || startTime.toLocalDate() == startDate)
    if (startTime != null && endTime != null) {
        require(endTime.toInstant() > startTime.toInstant())
        CalendarPeriod(startTime.toInstant().toEpochMilli(), endTime.toInstant().toEpochMilli(), false, false)
    } else {
        val midnightEnd = endTime?.toLocalTime() == LocalTime.MIDNIGHT
        val endExclusive = when {
            endTime != null -> endTime.toLocalDate().let { if (midnightEnd) it else it.plusDays(1) }
            endDate != null -> endDate.plusDays(1)
            else -> null
        }
        val knownStart = startDate ?: startTime?.toLocalDate()
        val start = knownStart ?: if (allowEndOnly) endExclusive?.minusDays(1) else null
        if (start == null) null else {
            val end = endExclusive ?: start.plusDays(1)
            require(end > start)
            CalendarPeriod(start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, knownStart == null)
        }
    }
} catch (_: DateTimeException) { null } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
