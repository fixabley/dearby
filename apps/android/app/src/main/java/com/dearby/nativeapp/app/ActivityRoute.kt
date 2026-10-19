package com.dearby.nativeapp.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.calendar.CalendarConflictSheet
import com.dearby.nativeapp.pages.activity.ActivityListPage
import com.dearby.nativeapp.pages.activity.ActivityDetailPage
import com.dearby.nativeapp.pages.activity.ActivityProgressPage
import com.dearby.nativeapp.pages.activity.ApplicationReportDialog
import com.dearby.nativeapp.pages.activity.MyActivitiesPage

@Composable fun ActivityRoute(model: ActivityViewModel, mine: Boolean = false, explore: () -> Unit = {}, active: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    // 내 활동: the activity whose stage timeline is open, and the stage whose calendar comparison is open.
    var progressId by remember { mutableStateOf<String?>(null) }
    var stageCalendar by remember { mutableStateOf<Int?>(null) }
    var prompt by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(selected, progressId) { active(selected != null || progressId != null); onDispose { active(false) } }
    val context = LocalContext.current
    // Links are already https-only from the activityModel model; failure to open stays on screen.
    val activity = selected?.let(state.known::get)
    val progress = progressId?.let(state.progress::get)
    val progressActivity = progressId?.let(state.known::get)
    val openLink: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onSuccess {
                // Only the official page opened while recruiting (detail or card CTA) leads to the prompt.
                val applied = activity?.takeIf { it.applyUrl == url } ?: state.known.values.find { selected == null && it.applyUrl == url }
                applied?.let { model.openedApplication(it.id) }
            }
            .onFailure { linkError = "링크를 열지 못했어요." }
    }
    // Detail over the timeline: back from the detail returns to the timeline it was opened from.
    BackHandler((selected != null || progressId != null) && !prompt && !calendar && stageCalendar == null) { if (selected != null) selected = null else progressId = null }
    when {
        activity != null -> ActivityDetailPage(activity, linkError, { selected = null }, openLink, { prompt = true },
            { model.confirm(activity.id, it) }, { calendar = true }, { sharing = true })
        progress != null && progressActivity != null -> ActivityProgressPage(progress, progressActivity, { progressId = null }, { selected = progress.id; linkError = null },
            { model.confirm(progress.id, it) }, { stage, value -> model.mark(progress.id, stage, value) }, { stageCalendar = it })
        mine -> MyActivitiesPage(state, { selected = it; linkError = null }, { progressId = it }, model::confirm, explore)
        else -> ActivityListPage(state, { selected = it; linkError = null }, model::search, model::reload, model::loadMore, openLink, model::toggle, model::retryProgram, model::setOpenOnly)
    }
    if (sharing && activity?.officialUrl != null) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("활동 링크") }, text = { Text(activity.officialUrl) }, confirmButton = { TextButton({ sharing = false }) { Text("닫기") } })
    if (calendar && activity != null) CalendarConflictSheet(model.schedules(activity.id)) { calendar = false }
    stageCalendar?.let { stage -> progress?.let { model.stageWindow(it.id, stage) } }?.let { window -> CalendarConflictSheet(listOf(window)) { stageCalendar = null } }
    if (prompt && activity != null) ApplicationReportDialog({ model.apply(activity.id, it); prompt = false }, { prompt = false })
}
