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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dearby.nativeapp.entities.activity.model.ScheduleModel
import com.dearby.nativeapp.shared.ui.DearbyButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The overlap check (contract "기기 캘린더 겹침"). Pure UI over [access]: [CalendarAccessState.Asking] explains why the
 * calendar is read and offers [request]; [CalendarAccessState.Denied] explains the refusal and offers [openSettings];
 * [CalendarAccessState.Compared] shows the overlaps found on the device. Titles and times stay on screen only.
 */
@Composable fun CalendarConflictSheet(
    schedules: List<ScheduleModel>, access: CalendarAccessState, request: () -> Unit, openSettings: () -> Unit, openPrivacy: () -> Unit, close: () -> Unit,
) {
    when (access) {
        is CalendarAccessState.Compared -> CalendarResultSheet(access.overlaps, schedules.first(), close)
        else -> Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row { Text("겹치는 시간 확인하기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(close) { Text("닫기") } }
                    if (access is CalendarAccessState.Denied) {
                        Text("캘린더 읽기 권한이 없어 비교할 수 없어요.", style = MaterialTheme.typography.titleMedium)
                        Text("설정에서 Dearby의 캘린더 권한을 허용하면 이 활동 시간과 겹치는 일정을 기기 안에서만 비교해요. 허용하지 않아도 신청은 그대로 할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                        DearbyButton(openSettings) { Text("설정 열기") }
                    } else {
                        Text("기기 캘린더와 비교해요", style = MaterialTheme.typography.titleMedium)
                        Text("이 활동 시간과 겹치는 기기 일정을 찾기 위해 캘린더 읽기 권한을 요청해요. 일정 제목·시각은 기기 안에서만 비교하고 서버로 보내거나 저장하지 않으며, 화면을 닫으면 버려요. 캘린더에 일정을 추가하지는 않아요.", style = MaterialTheme.typography.bodyMedium)
                        DearbyButton(request) { Text("캘린더 읽기 허용하고 확인") }
                        // 개인정보 동의(2026-10-19): the rationale links the web policy; the route opens it in a Custom Tab
                        TextButton(openPrivacy, contentPadding = PaddingValues(0.dp)) { Text("개인정보 처리방침 보기", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CalendarResultSheet(overlaps: List<CalendarOverlapState>, schedule: ScheduleModel, close: () -> Unit) {
    val overlap = overlaps.firstOrNull()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.of(schedule.timeZone)
    val date = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN).withZone(zone)
    ModalBottomSheet(
        onDismissRequest = close, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .84f })) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("겹치는 시간", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Text("${overlaps.size}건", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                IconButton(close) { Icon(Icons.Outlined.Close, "겹침 결과 닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.format(Instant.parse(schedule.startAt)), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(when { overlap == null -> "겹치는 시간이 없어요"; overlaps.size == 1 -> "${(overlap.end - overlap.start) / 60_000}분이 겹쳐요"; else -> "일정 ${overlaps.size}개와 겹쳐요" },
                    fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (overlap == null) "이 활동과 기기 캘린더에 겹치는 시간이 없어요."
                    else "${clock.format(Instant.ofEpochMilli(overlap.start))} – ${clock.format(Instant.ofEpochMilli(overlap.end))}에 ‘${overlap.busy.title ?: "제목 없는 일정"}’과 겹쳐요.",
                    style = MaterialTheme.typography.bodyMedium)
                if (overlaps.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    overlaps.drop(1).forEach { Text("· ${clock.format(Instant.ofEpochMilli(it.start))} – ${clock.format(Instant.ofEpochMilli(it.end))} ${it.busy.title ?: "제목 없는 일정"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(20.dp))
                if (overlap != null) CalendarOverlapTimeline(overlap)
                else Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(schedule.title, fontWeight = FontWeight.SemiBold)
                        Text("${clock.format(Instant.parse(schedule.startAt))} – ${clock.format(Instant.parse(schedule.endAt))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("기기 안에서만 비교해요 · 캘린더 일정은 변경되지 않아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
            }
            DearbyButton(close, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("확인했어요", fontWeight = FontWeight.SemiBold) }
            Text("확인 후 활동 상세로 돌아가요.", Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
