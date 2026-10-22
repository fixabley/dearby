package com.dearby.nativeapp.widgets.activity.progress

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

enum class ProgressStatus { PAST, CURRENT, UPCOMING }

/**
 * One status a step offers (contract "내 활동 서버 저장·일정 상태", 2026-10-22): the schedule's admin [name] or a default 대기·통과·탈락.
 * [kind] is WAITING, PASSED, FAILED or OTHER; [id] is null for a default status. The user's own mark, never the organizer's result.
 */
data class StatusOption(val id: String?, val name: String, val kind: String) {
    val failed get() = kind == "FAILED"
    val passed get() = kind == "PASSED"
}

/**
 * A prefilled system calendar event (contract "내 캘린더에 추가"): [title] "활동명 · 일정 제목", epoch millis, [allDay] for a date-only
 * schedule (Seoul midnights). The route adds the place and the official link and hands it to the calendar app.
 */
data class CalendarDraft(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/**
 * One step as shown. [kind] picks the icon (APPLICATION, DEADLINE, ANNOUNCEMENT, INTERVIEW, EVENT); [options] are its statuses and
 * [selected] the user's choice (the first waiting status until one is chosen); [hasWindow] when the calendar comparison can open on it;
 * [draft] for `캘린더에 추가`, null for an undated step.
 */
data class ProgressStage(val name: String, val kind: String, val dateText: String, val status: ProgressStatus, val options: List<StatusOption>, val selected: StatusOption?, val hasWindow: Boolean,
    val draft: CalendarDraft? = null)

/**
 * The progress track of one applied activity (contract "내 활동 진행 트랙"). [stages] is empty for an activity without
 * schedules, which keeps the plain 신청함 row. [ended] (a FAILED status, [failed], or every step over) moves it to
 * 끝난 활동; [nextDay] sorts 진행 중 with undated activities last.
 */
data class ProgressState(
    val id: String, val title: String, val subtitle: String, val stages: List<ProgressStage>, val current: Int?,
    val badge: String, val ended: Boolean, val failed: Boolean, val nextDay: LocalDate?,
) {
    /** The whole track as one accessibility node: "5단계 중 3단계 1차 면접 진행 중". */
    val trackDescription: String get() =
        if (current != null) "${stages.size}단계 중 ${current + 1}단계 ${stages[current].name} 진행 중" else "${stages.size}단계 끝남 · $badge"
}

fun stageIcon(kind: String): ImageVector = when (kind) {
    "APPLICATION" -> Icons.Rounded.EditNote
    "DEADLINE" -> Icons.Rounded.Flag
    "ANNOUNCEMENT" -> Icons.Rounded.Campaign
    "INTERVIEW" -> Icons.Rounded.RecordVoiceOver
    else -> Icons.Rounded.Event
}
