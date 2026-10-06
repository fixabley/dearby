package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.Quiet
import com.dearby.nativeapp.shared.ui.Soft
import com.dearby.nativeapp.shared.ui.Teal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The overlap check: explains, asks for calendar access through [check], then shows each overlap in turn. */
@Composable fun CalendarConflictSheet(schedules: List<ScheduleModel>, phase: CalendarPhase, overlaps: List<CalendarOverlapState>,
                                      check: () -> Unit, openSettings: () -> Unit, close: () -> Unit) {
    var index by remember(overlaps) { mutableIntStateOf(0) }
    if (phase == CalendarPhase.COMPARED) {
        CalendarResultSheet(overlaps.getOrNull(index), index, overlaps.size, schedules.first(),
            next = { if (index + 1 < overlaps.size) index++ else close() }, close = close)
    } else Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row { Text("겹치는 시간 확인하기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(close) { Text("닫기") } }
                Text("이 활동 일정과 내 캘린더 일정이 겹치는지 기기 안에서만 비교해요. 일정을 저장하거나 보내지 않고, 캘린더에 쓰지 않아요.", style = MaterialTheme.typography.bodyMedium)
                if (phase == CalendarPhase.DENIED) {
                    Text("캘린더 접근이 꺼져 있어요", style = MaterialTheme.typography.titleMedium)
                    Text("설정에서 Dearby의 캘린더 접근을 켜면 겹치는 시간을 확인할 수 있어요. 신청은 그대로 할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(openSettings, Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("설정 열기") }
                } else DearbyButton(check, Modifier.fillMaxWidth(), enabled = phase != CalendarPhase.CHECKING) {
                    if (phase == CalendarPhase.CHECKING) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("내 캘린더로 확인")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalendarResultSheet(overlap: CalendarOverlapState?, index: Int, count: Int, schedule: ScheduleModel, next: () -> Unit, close: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.of(schedule.timeZone)
    val date = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(zone)
    ModalBottomSheet(
        onDismissRequest = close, sheetState = sheet, containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).background(Color(0xFFC9CDD0), RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .84f })) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("겹치는 시간", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text(if (overlap == null) "0건" else "${index + 1} / $count", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                IconButton(close) { Icon(Icons.Outlined.Close, "겹침 결과 닫기", tint = Quiet) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(20.dp), tint = Quiet)
                    Text(date.format(overlap?.let { Instant.ofEpochMilli(it.start) } ?: Instant.parse(schedule.startAt)), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(if (overlap == null) "겹치는 시간이 없어요" else "${(overlap.end - overlap.start) / 60_000}분이 겹쳐요",
                    fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (overlap == null) "이 활동과 내 캘린더에 겹치는 시간이 없어요."
                    else "${clock.format(Instant.ofEpochMilli(overlap.start))} – ${clock.format(Instant.ofEpochMilli(overlap.end))}에 다른 일정과 겹쳐요.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                if (overlap != null) CalendarOverlapTimeline(overlap)
                else Surface(color = Soft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(schedule.title, fontWeight = FontWeight.SemiBold)
                        Text("${clock.format(Instant.parse(schedule.startAt))} – ${clock.format(Instant.parse(schedule.endAt))}", color = Quiet)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = Quiet)
                    Text("기기 안에서만 비교했고 일정을 저장하거나 보내지 않아요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
            }
            DearbyButton(next, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("확인했어요", fontWeight = FontWeight.SemiBold) }
            Text(if (index + 1 < count) "다음 겹치는 일정으로 이동해요." else "확인 후 활동 상세로 돌아가요.", Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), color = Teal, style = MaterialTheme.typography.bodySmall)
        }
    }
}
