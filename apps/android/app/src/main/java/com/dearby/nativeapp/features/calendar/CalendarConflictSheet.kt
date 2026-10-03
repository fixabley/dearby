package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.Teal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun CalendarConflictSheet(schedules: List<ScheduleModel>, close: () -> Unit) {
    var selected by remember { mutableStateOf(true) }
    var compared by remember { mutableStateOf(false) }
    val overlaps = remember(schedules) { demoOverlaps(schedules) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row { Text("겹치는 시간 확인하기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(close) { Text("닫기") } }
                Text("예시 바쁜 시간과 비교해요. 기기 캘린더를 읽거나 권한을 요청하지 않습니다.", style = MaterialTheme.typography.bodyMedium)
                Text("비교할 캘린더", style = MaterialTheme.typography.titleMedium)
                Row { Checkbox(selected, { selected = it; compared = false }); Text("예시 캘린더", Modifier.padding(top = 12.dp)) }
                if (!selected) Text("비교할 캘린더를 하나 이상 선택해 주세요.")
                DearbyButton({ compared = true }, enabled = selected) { Text("선택한 캘린더로 확인") }
                if (compared) {
                    if (overlaps.isEmpty()) {
                        Text("선택한 캘린더와 겹치는 시간이 없어요", color = Teal)
                        Text("고정 예시 바쁜 시간 기준이에요.", style = MaterialTheme.typography.bodySmall)
                    } else overlaps.forEachIndexed { index, item ->
                        Text("겹치는 시간 ${index + 1} / ${overlaps.size}", style = MaterialTheme.typography.titleMedium)
                        Text(item.activity.title)
                        CalendarTimeRow("활동 일정", item.activity.start, item.activity.end, item.activity.zone)
                        CalendarTimeRow("내 바쁜 시간 (예시)", item.busy.start, item.busy.end, item.activity.zone)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.padding(16.dp)) { CalendarTimeRow("겹치는 시간", item.start, item.end, item.activity.zone) } }
                    }
                    Row { Spacer(Modifier.weight(1f)); TextButton(close) { Text("확인 완료") } }
                }
            }
        }
    }
}
@Composable private fun CalendarTimeRow(label: String, start: Long, end: Long, zone: String) {
    val format = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN).withZone(ZoneId.of(zone))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text("${format.format(Instant.ofEpochMilli(start))} → ${format.format(Instant.ofEpochMilli(end))}")
        Text(zone, style = MaterialTheme.typography.labelSmall)
    }
}
