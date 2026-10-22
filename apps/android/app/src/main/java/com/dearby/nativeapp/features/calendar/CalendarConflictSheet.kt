package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dearby.nativeapp.entities.activity.model.ScheduleModel
import com.dearby.nativeapp.entities.activity.model.instant
import com.dearby.nativeapp.shared.ui.CenteredMessage
import com.dearby.nativeapp.shared.ui.MessageAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The overlap check (contract "기기 캘린더 겹침"). Pure UI over [access]: [CalendarAccessState.Asking] explains why the
 * calendar is read and offers [request]; [CalendarAccessState.Denied] explains the refusal and offers [openSettings];
 * [CalendarAccessState.Compared] shows the overlaps found on the device. Titles and times stay on screen only.
 * Every state is a `CenteredMessage` (contract "비스크롤 화면 배치·CTA"): no card, one text scale, the action as a full-width CTA at the bottom.
 */
@Composable fun CalendarConflictSheet(
    schedules: List<ScheduleModel>, access: CalendarAccessState, request: () -> Unit, openSettings: () -> Unit, openPrivacy: () -> Unit, close: () -> Unit,
) {
    when (access) {
        is CalendarAccessState.Compared -> CalendarResultSheet(access.overlaps, schedules.first(), close)
        // Drawn in its own window, so it pads the safe area itself.
        else -> Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                // The message keeps to the component's three lines; the rest of the rationale follows it in `content` at the same size.
                if (access is CalendarAccessState.Denied) CenteredMessage(Icons.Rounded.Event, "캘린더 읽기 권한이 없어요", "권한이 없어 이 활동 시간과 겹치는 일정을 비교할 수 없어요.", Modifier.fillMaxSize().safeDrawingPadding(),
                    content = { Text("설정에서 Dearby의 캘린더 권한을 허용하면 겹치는 일정을 기기 안에서만 비교해요. 허용하지 않아도 신청은 그대로 할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) },
                    primary = MessageAction("설정 열기", openSettings), secondary = MessageAction("닫기", close))
                else CenteredMessage(Icons.Rounded.Event, "기기 캘린더와 비교해요", "이 활동 시간과 겹치는 기기 일정을 찾기 위해 캘린더 읽기 권한을 요청해요.", Modifier.fillMaxSize().safeDrawingPadding(),
                    content = {
                        Text("일정 제목·시각은 기기 안에서만 비교하고 서버로 보내거나 저장하지 않으며, 화면을 닫으면 버려요. 캘린더에 일정을 추가하지는 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        // 개인정보 동의(2026-10-19): the rationale links the web policy; the route opens it in a Custom Tab.
                        TextButton(openPrivacy) { Text("개인정보 처리방침 보기") }
                    },
                    primary = MessageAction("캘린더 읽기 허용하고 확인", request), secondary = MessageAction("닫기", close))
            }
        }
    }
}

/** The result: a check or warning symbol, the overlap sentence as the title, the first overlap's time as the message, then the timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalendarResultSheet(overlaps: List<CalendarOverlapState>, schedule: ScheduleModel, close: () -> Unit) {
    val overlap = overlaps.firstOrNull()
    val zone = ZoneId.of(schedule.timeZone)
    val date = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(zone)
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        CenteredMessage(if (overlap == null) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            when { overlap == null -> "겹치는 시간이 없어요"; overlaps.size == 1 -> "${(overlap.end - overlap.start) / 60_000}분이 겹쳐요"; else -> "일정 ${overlaps.size}개와 겹쳐요" },
            if (overlap == null) "이 활동과 기기 캘린더에 겹치는 시간이 없어요."
            else "${clock.format(Instant.ofEpochMilli(overlap.start))} – ${clock.format(Instant.ofEpochMilli(overlap.end))}에 ‘${overlap.busy.title ?: "제목 없는 일정"}’과 겹쳐요.",
            content = {
                // The overlapping day (a date-only schedule repeats its overlap-check window daily), else the first window's.
                Text(overlap?.let { date.format(Instant.ofEpochMilli(it.start)) } ?: instant(schedule.startAt)?.let(date::format).orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                // A long date range can overlap dozens of times: five more lines, then the count.
                if (overlaps.size > 1) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    overlaps.drop(1).take(5).forEach { Text("${date.format(Instant.ofEpochMilli(it.start))} ${clock.format(Instant.ofEpochMilli(it.start))} – ${clock.format(Instant.ofEpochMilli(it.end))} ${it.busy.title ?: "제목 없는 일정"}", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
                    if (overlaps.size > 6) Text("외 ${overlaps.size - 6}개", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                if (overlap != null) CalendarOverlapTimeline(overlap)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(schedule.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    Text("${instant(schedule.startAt)?.let(clock::format).orEmpty()} – ${instant(schedule.endAt)?.let(clock::format).orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("기기 안에서만 비교해요 · 캘린더 일정은 변경되지 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            },
            primary = MessageAction("확인했어요", close))
    }
}
