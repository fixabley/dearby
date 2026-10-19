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

/** The user's own mark on one step (default [WAIT]); never the organizer's result. */
enum class ProgressMark(val label: String) { PASS("통과"), WAIT("대기"), FAIL("탈락") }

/** One step as shown. [kind] picks the icon (APPLICATION, DEADLINE, ANNOUNCEMENT, INTERVIEW, EVENT); [hasWindow] when the calendar comparison can open on it. */
data class ProgressStage(val name: String, val kind: String, val dateText: String, val status: ProgressStatus, val mark: ProgressMark, val hasWindow: Boolean)

/**
 * The progress track of one applied activity (contract "내 활동 진행 트랙"). [stages] is empty for an activity without
 * schedules, which keeps the plain 신청함/참여 확정 row. [ended] (a 탈락 mark, [failed], or every step over) moves it to
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
