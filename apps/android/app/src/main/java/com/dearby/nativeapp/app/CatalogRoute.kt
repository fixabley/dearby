package com.dearby.nativeapp.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.calendar.BusyTimeState
import com.dearby.nativeapp.features.calendar.CalendarConflictSheet
import com.dearby.nativeapp.features.calendar.CalendarOverlapState
import com.dearby.nativeapp.features.calendar.CalendarPhase
import com.dearby.nativeapp.features.calendar.calendarOverlaps
import com.dearby.nativeapp.features.calendar.calendarWindows
import com.dearby.nativeapp.features.calendar.deviceBusyTimes
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.Instant
import com.dearby.nativeapp.pages.catalog.CatalogPage
import com.dearby.nativeapp.pages.catalog.CatalogPhase
import com.dearby.nativeapp.pages.catalog.ActivityDetailPage
import com.dearby.nativeapp.pages.catalog.ApplicationReportDialog
import com.dearby.nativeapp.pages.catalog.MyActivitiesPage
import kotlinx.coroutines.launch

@Composable fun CatalogRoute(model: CatalogViewModel, mine: Boolean = false, explore: () -> Unit = {}, active: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    // Contract #148: device events are compared here and dropped with the sheet; nothing is stored or sent.
    var calendarPhase by remember { mutableStateOf(CalendarPhase.IDLE) }
    var overlaps by remember { mutableStateOf(emptyList<CalendarOverlapState>()) }
    var pendingSchedules by remember { mutableStateOf(emptyList<ScheduleModel>()) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (state.phase == CatalogPhase.LOADING && state.activities.isEmpty()) model.load() }
    DisposableEffect(selected) { active(selected != null); onDispose { active(false) } }
    val context = LocalContext.current
    suspend fun compare(schedules: List<ScheduleModel>) {
        val windows = calendarWindows(schedules)
        val busy = if (windows.isEmpty()) emptyList() else deviceBusyTimes(context, windows.minOf { it.start }, windows.maxOf { it.end })
        overlaps = calendarOverlaps(schedules, busy)
        calendarPhase = CalendarPhase.COMPARED
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { compare(pendingSchedules) } else calendarPhase = CalendarPhase.DENIED
    }
    suspend fun checkCalendar(schedules: List<ScheduleModel>) {
        calendarPhase = CalendarPhase.CHECKING
        ApiOrigin.debugCalendarBusy?.takeIf { BuildConfig.DEBUG }?.let { fixture ->
            if (fixture == "denied") { calendarPhase = CalendarPhase.DENIED; return }
            overlaps = calendarOverlaps(schedules, fixture.split(';').map { it.split('/') }
                .map { BusyTimeState(Instant.parse(it[0]).toEpochMilli(), Instant.parse(it[1]).toEpochMilli()) })
            calendarPhase = CalendarPhase.COMPARED
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) compare(schedules)
        else { pendingSchedules = schedules; askCalendar.launch(Manifest.permission.READ_CALENDAR) }
    }
    // Links are already https-only from the catalog model; failure to open stays on screen.
    val activity = state.activities.find { it.id == selected }
    val openLink: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onSuccess {
                // Only application links (detail CTA or quick apply), not the official notice, lead to the prompt.
                val applied = activity?.takeIf { it.applyUrl == url } ?: state.activities.find { selected == null && it.quickApplyUrl == url }
                applied?.let { model.openedApplication(it.id) }
            }
            .onFailure { linkError = "링크를 열지 못했어요." }
    }
    BackHandler(selected != null && !prompt && !calendar) { selected = null }
    when {
        activity != null -> ActivityDetailPage(activity, linkError, { selected = null }, openLink, { prompt = true },
            { model.confirm(activity.id, it) }, { calendar = true }, { sharing = true })
        mine -> MyActivitiesPage(state.appliedActivities, { selected = it; linkError = null }, model::confirm, explore)
        else -> CatalogPage(state, { selected = it; linkError = null }, model::filter, { scope.launch { model.load() } }, openLink)
    }
    if (sharing && activity?.officialUrl != null) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("활동 링크") }, text = { Text(activity.officialUrl) }, confirmButton = { TextButton({ sharing = false }) { Text("닫기") } })
    if (calendar && activity != null) {
        val schedules = model.schedules(activity.id)
        CalendarConflictSheet(schedules, calendarPhase, overlaps,
            check = { scope.launch { checkCalendar(schedules) } },
            openSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) } },
            close = { calendar = false; calendarPhase = CalendarPhase.IDLE; overlaps = emptyList() })
    }
    if (prompt && activity != null) ApplicationReportDialog({ model.apply(activity.id, it); prompt = false }, { prompt = false })
}
