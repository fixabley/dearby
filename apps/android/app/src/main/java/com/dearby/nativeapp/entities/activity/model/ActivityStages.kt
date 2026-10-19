package com.dearby.nativeapp.entities.activity.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One step of an applied activity (contract "내 활동 진행 트랙"). [label] is the badge word (마감·발표·면접, or the title for
 * EVENT). [day] is the Seoul date the badge counts down to and [lastDay] the last Seoul date the step is still on; both
 * are null for an undated step. [window] is the timed range for the calendar comparison, only when both ends are known.
 */
data class Stage(val name: String, val kind: String, val label: String, val dateText: String, val day: LocalDate?, val lastDay: LocalDate?, val window: ScheduleModel?) {
    /** Over by [today]: the step's last day has passed. An undated step never ends by itself. */
    fun finished(today: LocalDate) = lastDay != null && lastDay < today
}

private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
private const val UNDATED = "날짜 미확인"

/**
 * `지원` (the application, counting to its deadline) first, the other schedules by start with undated ones after them
 * in API order, and the activity period last as `활동` (or its own title). An activity with only an application and a
 * period still has 지원 → 활동, so it stays 진행 중 until the period ends.
 */
fun ActivityModel.stages(): List<Stage> {
    val applying = application?.let { window ->
        // The deadline only (timed, else the all-day window's last day); the start is left out on purpose.
        val day = instant(window.endAt)?.atZone(seoul)?.toLocalDate() ?: localDate(window.endDateExclusive)?.minusDays(1)
        Stage("지원", "APPLICATION", "마감", if (day == null) UNDATED else scheduleText(null, window.endAt, null, window.endDateExclusive), day, day, null)
    }
    val period = activitySchedule?.let { it.copy(title = it.title.ifBlank { "활동" }).toStage() }
    return listOfNotNull(applying) + stageSchedules.sortedWith(compareBy(nullsLast()) { it.startInstant() }).map { it.toStage() } + listOfNotNull(period)
}

/** The earliest step the user has not marked passed and that is not over by [today]; null when every step is done. */
fun currentStage(stages: List<Stage>, passed: Set<Int>, today: LocalDate): Int? =
    stages.indices.firstOrNull { it !in passed && !stages[it].finished(today) }

/** Trailing badge for the current step: `D-3 · 면접`, `D-DAY · 발표` while the step is on, `지남 · 마감`, or `날짜 미확인 · 면접`. */
fun stageBadge(stage: Stage, today: LocalDate): String {
    val day = stage.day ?: return "$UNDATED · ${stage.label}"
    val left = ChronoUnit.DAYS.between(today, day)
    val count = when { left > 0 -> "D-$left"; today <= (stage.lastDay ?: day) -> "D-DAY"; else -> "지남" }
    return "$count · ${stage.label}"
}

private fun StageSchedule.startInstant(): Instant? =
    instant(startAt) ?: instant(endAt) ?: localDate(startDate)?.atStartOfDay(seoul)?.toInstant()

private fun StageSchedule.toStage(): Stage {
    val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(seoul)
    val start = instant(startAt)
    val end = instant(endAt)
    val startDay = localDate(startDate)
    val endDay = localDate(endDateExclusive)?.minusDays(1) ?: startDay
    val label = when (kind) { "DEADLINE" -> "마감"; "ANNOUNCEMENT" -> "발표"; "INTERVIEW" -> "면접"; else -> title }
    // Text by the structured-first rule (contract "일정 표시 우선순위"); the collected label only when no date exists.
    val text = scheduleText().ifBlank { UNDATED }
    return when {
        start != null || end != null -> Stage(title, kind, label, text,
            (start ?: end)!!.atZone(seoul).toLocalDate(), (end ?: start)!!.atZone(seoul).toLocalDate(),
            if (startAt != null && endAt != null) ScheduleModel(title, startAt, endAt, zone.id) else null)
        startDay != null -> Stage(title, kind, label, text, startDay, endDay, null)
        else -> Stage(title, kind, label, text, null, null, null)
    }
}

private fun localDate(raw: String?): LocalDate? = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
