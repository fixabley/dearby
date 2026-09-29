package com.dearby.nativeapp.features.calendar

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.shared.calendar.DeviceCalendarStore
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.Teal
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun CalendarConflictSheet(schedules: List<ScheduleModel>, close: () -> Unit) {
    val context = LocalContext.current
    val store = remember { DeviceCalendarStore(context.applicationContext) }
    val state = remember(schedules) { CalendarConflictState(schedules, store) }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scope.launch { state.connect() } }
    fun connect() { if (state.windows.isNotEmpty() && !store.authorized()) permission.launch(Manifest.permission.READ_CALENDAR) else scope.launch { state.connect() } }
    LaunchedEffect(state) { connect() }
    DisposableEffect(owner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.clear()
            if (event == Lifecycle.Event.ON_START && state.phase == "idle") scope.launch { state.connect() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); state.clear() }
    }
    Dialog(onDismissRequest = { state.clear(); close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row { Text("겹치는 시간 확인하기", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ state.clear(); close() }) { Text("닫기") } }
                Text("개인 일정의 제목과 내용은 가져오지 않고, 바쁜 시간만 이 화면에서 비교해요.", style = MaterialTheme.typography.bodyMedium)
                if (state.unknownCount > 0 && state.windows.isNotEmpty()) Text("시간이 확인되지 않은 일정 ${state.unknownCount}개는 비교에서 제외됐어요.")
                when (state.phase) {
                    "idle", "loading" -> CircularProgressIndicator()
                    "unknown" -> Text("활동의 정확한 시작·종료 시간이 없어 비교할 수 없어요. 공식 일정이 확인되면 다시 확인해 주세요. 겹치는 시간이 없다는 뜻은 아니에요.")
                    "permission" -> {
                        Text("캘린더 읽기 권한이 필요해요. 권한이 없으면 겹치는 시간을 확인할 수 없어요.")
                        TextButton({ connect() }) { Text("읽기 권한 요청") }
                        TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text("설정 열기") }
                    }
                    "no_calendars" -> { Text("기기에 표시 중인 캘린더가 없어요. 캘린더 앱에서 계정을 연결하고 표시를 켜 주세요."); TextButton({ connect() }) { Text("다시 확인") } }
                    "failed" -> { Text("캘린더를 확인하지 못했어요. 권한과 연결 상태를 확인하고 다시 시도해 주세요."); TextButton({ connect() }) { Text("다시 확인") } }
                    else -> {
                        Text("비교할 캘린더", style = MaterialTheme.typography.titleMedium)
                        state.calendars.forEach { calendar -> Row { Checkbox(calendar.id in state.selected, { state.toggle(calendar.id) }); Text(calendar.title, Modifier.padding(top = 12.dp)) } }
                        if (state.selected.isEmpty()) Text("비교할 캘린더를 하나 이상 선택해 주세요.")
                        DearbyButton({ scope.launch { state.compare() } }, enabled = state.selected.isNotEmpty()) { Text("선택한 캘린더로 확인") }
                        if (state.phase == "result") {
                            if (state.overlaps.isEmpty()) {
                                Text(if (state.unknownCount == 0) "선택한 캘린더와 겹치는 시간이 없어요" else "확인 가능한 시간에는 겹침이 없어요", color = Teal)
                                Text("기기에 동기화된 선택한 캘린더 기준이에요.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                val item = state.overlaps[state.index]
                                Text("겹치는 시간 ${state.index + 1} / ${state.overlaps.size}", style = MaterialTheme.typography.titleMedium)
                                Text(item.activity.title)
                                CalendarTimeRow("활동 일정", item.activity.start, item.activity.end, item.activity.zone)
                                CalendarTimeRow("내 바쁜 시간", item.busy.start, item.busy.end, item.activity.zone)
                                Surface(color = MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.padding(16.dp)) { CalendarTimeRow("겹치는 시간", item.start, item.end, item.activity.zone) } }
                                Row {
                                    if (state.index > 0) TextButton({ state.index-- }) { Text("이전") }
                                    Spacer(Modifier.weight(1f))
                                    TextButton({ if (state.index + 1 == state.overlaps.size) { state.clear(); close() } else state.index++ }) { Text(if (state.index + 1 == state.overlaps.size) "확인 완료" else "다음 겹침") }
                                }
                            }
                        }
                    }
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
