package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import com.dearby.nativeapp.entities.activity.model.Recurrence
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

/**
 * The result (contract "반복 일정과 겹침 결과 묶음"): a check or warning symbol and the title; one overlap keeps its sentence and the
 * timeline; several show the summary "n일 겹침 · 반복 일정 a개 · 단일 일정 b개", one row per repeating device event (tap to list its
 * dates), then one-off events nearest first, five until "더 보기".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalendarResultSheet(overlaps: List<CalendarOverlapState>, schedule: ScheduleModel, close: () -> Unit) {
    val groups = remember(overlaps) { groupOverlaps(overlaps) }
    val single = groups.singleOrNull()?.takeIf { !it.repeating }?.overlaps?.singleOrNull()
    val zone = ZoneId.of(schedule.timeZone)
    val date = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(zone)
    fun span(start: Long, end: Long) = "${clock.format(Instant.ofEpochMilli(start))} – ${clock.format(Instant.ofEpochMilli(end))}"
    val (repeating, oneOff) = groups.partition { it.repeating }
    var allOneOff by remember(overlaps) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        CenteredMessage(if (groups.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            when { groups.isEmpty() -> "겹치는 시간이 없어요"; single != null -> "${(single.end - single.start) / 60_000}분이 겹쳐요"; else -> "겹치는 일정이 있어요" },
            when {
                groups.isEmpty() -> "이 활동과 기기 캘린더에 겹치는 시간이 없어요."
                single != null -> "${span(single.start, single.end)}에 ‘${single.busy.title ?: "제목 없는 일정"}’과 겹쳐요."
                else -> listOfNotNull("${overlapDays(groups.flatMap { it.overlaps }, zone)}일 겹침", repeating.size.takeIf { it > 0 }?.let { "반복 일정 ${it}개" },
                    oneOff.size.takeIf { it > 0 }?.let { "단일 일정 ${it}개" }).joinToString(" · ")
            },
            content = {
                when {
                    single != null -> {
                        Text(date.format(Instant.ofEpochMilli(single.start)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        CalendarOverlapTimeline(single)
                    }
                    groups.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(instant(schedule.startAt)?.let(date::format).orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        Text(schedule.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        Text("${instant(schedule.startAt)?.let(clock::format).orEmpty()} – ${instant(schedule.endAt)?.let(clock::format).orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> Column(Modifier.fillMaxWidth()) {
                        repeating.forEach { group -> RepeatingRow(group, describe(group, ::span), { date.format(Instant.ofEpochMilli(it)) }, ::span) }
                        (if (allOneOff) oneOff else oneOff.take(5)).forEach { group ->
                            val overlap = group.overlaps.first()
                            ListItem(headlineContent = { Text(group.title ?: "제목 없는 일정") },
                                supportingContent = { Text("${date.format(Instant.ofEpochMilli(overlap.busy.start))} ${span(overlap.busy.start, overlap.busy.end)}") })
                        }
                        if (!allOneOff && oneOff.size > 5) TextButton({ allOneOff = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("더 보기 (${oneOff.size - 5}개)") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("기기 안에서만 비교해요 · 캘린더 일정은 변경되지 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            },
            primary = MessageAction("확인했어요", close))
    }
}

/** "매주 화 10:00 – 11:00" from the device event's rule and its first occurrence; "반복 일정" when the rule is unreadable. */
private fun describe(group: OverlapGroupState, span: (Long, Long) -> String): String {
    val busy = group.overlaps.first().busy
    return Recurrence.parse(group.rule)?.let { "${it.describe()} ${span(busy.start, busy.end)}" } ?: "반복 일정"
}

/** One repeating device event: "n번 겹침(first~last)"; tapping lists every overlapping date. */
@Composable private fun RepeatingRow(group: OverlapGroupState, pattern: String, day: (Long) -> String, span: (Long, Long) -> String) {
    var open by remember(group) { mutableStateOf(false) }
    val first = group.overlaps.first().start; val last = group.overlaps.last().start
    ListItem(headlineContent = { Text("${group.title ?: "제목 없는 일정"} · $pattern") },
        supportingContent = { Text("${group.overlaps.distinctBy { it.busy.start }.size}번 겹침(${day(first)}${if (day(first) == day(last)) "" else "~${day(last)}"})") },
        trailingContent = { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "날짜 접기" else "겹친 날짜 보기") },
        modifier = Modifier.clickable { open = !open })
    if (open) group.overlaps.forEach { overlap ->
        Text("${day(overlap.start)} ${span(overlap.start, overlap.end)}", Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
