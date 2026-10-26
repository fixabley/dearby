package com.dearby.nativeapp.entities.activity.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One step of an applied activity (contract "내 활동 진행 트랙"). [label] is the badge word (마감·발표·면접, or the title for
 * EVENT). [day] is the Seoul date the badge counts down to and [lastDay] the last Seoul date the step is still on; both
 * are null for an undated step. [windows] are the calendar-comparison windows ([StageSchedule.windows]; empty for 지원).
 * [scheduleId] and [statuses] are the schedule's, for the user's status choice (contract "내 활동 서버 저장·일정 상태"); [statuses] is empty
 * for a step that is not a 선발 단계, which shows only its date and 예정/지남. [selection] is that judgment (contract "선발 과정·활동 일정 분리":
 * the server's `phase`, see [StageSchedule.isSelection]), used to split 선발 과정 from 활동 일정.
 * [eventStart]/[eventEnd]/[allDay] prefill the system "add event" screen (contract "내 캘린더에 추가"): a timed step uses its times
 * (one end missing → a point), a date-only step is all-day from Seoul midnight; null for an undated step. A repeating step
 * (contract "반복 일정과 겹침 결과 묶음") prefills its first occurrence, never all-day, with [recurrence] as the event's RRULE.
 */
data class Stage(val name: String, val kind: String, val label: String, val dateText: String, val day: LocalDate?, val lastDay: LocalDate?,
    val windows: List<ScheduleModel> = emptyList(), val scheduleId: String? = null, val statuses: List<ScheduleStatus> = ScheduleStatus.DEFAULTS,
    val eventStart: Instant? = null, val eventEnd: Instant? = null, val allDay: Boolean = false, val recurrence: String? = null,
    /** The schedule has a 트랙·세션 시간표 (contract "트랙·세션 시간표"). */
    val hasTimetable: Boolean = false,
    /** A 선발 과정 step (`phase = SELECTION`); the 지원 step always, the activity period never. */
    val selection: Boolean = false) {
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
        val deadline = instant(window.endAt)
        val day = deadline?.atZone(seoul)?.toLocalDate() ?: localDate(window.endDateExclusive)?.minusDays(1)
        Stage("지원", "APPLICATION", "마감", if (day == null) UNDATED else scheduleText(null, window.endAt, null, window.endDateExclusive, recruitment = true), day, day,
            scheduleId = window.id, statuses = window.statuses.ifEmpty { ScheduleStatus.DEFAULTS }, selection = true,
            eventStart = deadline ?: day?.atStartOfDay(seoul)?.toInstant(), eventEnd = deadline ?: day?.plusDays(1)?.atStartOfDay(seoul)?.toInstant(), allDay = deadline == null)
    }
    // The activity-period slot is always ACTIVITY (contract "선발 과정·활동 일정 분리": 고정), whatever an older API sent.
    val period = activitySchedule?.let { it.copy(title = it.title.ifBlank { "활동" }, phase = ACTIVITY).toStage() }
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
    val start = instant(startAt)
    val end = instant(endAt)
    val startDay = localDate(startDate)
    val endDay = localDate(endDateExclusive)?.minusDays(1) ?: startDay
    val label = when (kind) { "DEADLINE" -> "마감"; "ANNOUNCEMENT" -> "발표"; "INTERVIEW" -> "면접"; else -> title }
    // Text by the structured-first rule (contract "일정 표시 우선순위"); the collected label only when no date exists.
    val text = scheduleText().ifBlank { UNDATED }
    val selection = isSelection()
    // Only a 선발 단계 offers 대기·통과·탈락 (the admin's list when set, else the defaults); any other step has no choice.
    val statuses = if (selection) statuses.ifEmpty { ScheduleStatus.DEFAULTS } else emptyList()
    val windows = windows()
    val repeating = windows.firstOrNull()?.takeIf { Recurrence.parse(recurrence) != null }
    val stage = when {
        // Counts down to the first occurrence and stays on until the last one.
        repeating != null -> Stage(title, kind, label, text, instant(repeating.startAt)?.atZone(seoul)?.toLocalDate(), instant(windows.last().endAt)?.atZone(seoul)?.toLocalDate(), windows, id, statuses, instant(repeating.startAt), instant(repeating.endAt), recurrence = recurrence)
        start != null || end != null -> Stage(title, kind, label, text,
            (start ?: end)!!.atZone(seoul).toLocalDate(), (end ?: start)!!.atZone(seoul).toLocalDate(), windows, id, statuses, start ?: end, end ?: start)
        startDay != null -> Stage(title, kind, label, text, startDay, endDay, windows, id, statuses,
            startDay.atStartOfDay(seoul).toInstant(), endDay!!.plusDays(1).atStartOfDay(seoul).toInstant(), allDay = true)
        else -> Stage(title, kind, label, text, null, null, emptyList(), id, statuses)
    }
    return stage.copy(hasTimetable = hasTimetable, selection = selection)
}

/**
 * 선발 과정 or 활동 일정 (contract "선발 과정·활동 일정 분리", 2026-10-26): the server's `phase` decides. An older API without `phase`
 * falls back to the app's earlier rule (통과 여부는 선발 단계에만): 마감(DEADLINE)·발표(ANNOUNCEMENT)·면접(INTERVIEW) are selection, and so is any
 * other schedule whose statuses the admin set (ids present) — the only way an EVENT-kind 코딩테스트 was marked before `phase` existed.
 */
fun StageSchedule.isSelection(): Boolean = when (phase) {
    SELECTION -> true
    ACTIVITY -> false
    else -> kind in SELECTION_KINDS || statuses.any { it.id != null }
}
private const val SELECTION = "SELECTION"
private const val ACTIVITY = "ACTIVITY"
private val SELECTION_KINDS = setOf("DEADLINE", "ANNOUNCEMENT", "INTERVIEW")

private fun localDate(raw: String?): LocalDate? = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
