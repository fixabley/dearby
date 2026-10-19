package com.dearby.nativeapp.app

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.calendar.CalendarConflictFlow
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.pages.activity.ActivityListPage
import com.dearby.nativeapp.pages.activity.ActivityDetailPage
import com.dearby.nativeapp.pages.activity.ActivityProgressPage
import com.dearby.nativeapp.pages.activity.ApplicationReportDialog
import com.dearby.nativeapp.pages.activity.MyActivitiesPage

/**
 * [settings] and [notify] are the fixed 설정·알림 header buttons of both tab roots (contract "설정"). [openInMine] asks the shell to show an activity's
 * detail on the 내 활동 tab (contract "내 활동 직접 추가" → 내 활동에서 보기); [open] is that request arriving on the 내 활동 instance, consumed once.
 */
@Composable fun ActivityRoute(
    model: ActivityViewModel, mine: Boolean = false, explore: () -> Unit = {}, settings: () -> Unit = {}, notify: () -> Unit = {},
    openInMine: (String) -> Unit = {}, open: String? = null, consumeOpen: () -> Unit = {}, active: (Boolean) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    // 내 활동: the activity whose stage timeline is open, and the stage whose calendar comparison is open.
    var progressId by remember { mutableStateOf<String?>(null) }
    var stageCalendar by remember { mutableStateOf<Int?>(null) }
    var prompt by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) { open?.let { selected = it; linkError = null; consumeOpen() } }
    DisposableEffect(selected, progressId) { active(selected != null || progressId != null); onDispose { active(false) } }
    val context = LocalContext.current
    // Links are already https-only from the activityModel model; failure to open stays on screen.
    val activity = selected?.let(state.known::get)
    val progress = progressId?.let(state.progress::get)
    val progressActivity = progressId?.let(state.known::get)
    val openLink: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onSuccess {
                // Only the detail CTA while recruitment is not closed (open, upcoming or unknown) leads to the "신청하셨나요?" prompt.
                activity?.takeIf { it.applyUrl == url }?.let { model.openedApplication(it.id) }
            }
            .onFailure { linkError = "링크를 열지 못했어요." }
    }
    // Contract "활동 장소": the curated place opens in a map app pinned at its coordinates; no map app leaves the error on screen.
    val openMap = {
        activity?.place?.let { place ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.name)})".toUri())) }
                .onFailure { linkError = "지도 앱을 열지 못했어요." }
        }
        Unit
    }
    // Detail over the timeline: back from the detail returns to the timeline it was opened from.
    BackHandler((selected != null || progressId != null) && !prompt && !calendar && stageCalendar == null) { if (selected != null) selected = null else progressId = null }
    when {
        activity != null -> ActivityDetailPage(activity, linkError, { selected = null }, openLink, { prompt = true },
            { model.confirm(activity.id, it) }, { calendar = true }, { sharing = true }, openMap,
            addToMine = { model.apply(activity.id, true) }, viewInMine = { openInMine(activity.id) }, removeFromMine = { model.apply(activity.id, false) })
        progress != null && progressActivity != null -> ActivityProgressPage(progress, progressActivity, { progressId = null }, { selected = progress.id; linkError = null },
            { model.confirm(progress.id, it) }, { stage, value -> model.mark(progress.id, stage, value) }, { stageCalendar = it }, { model.apply(progress.id, false); progressId = null })
        mine -> MyActivitiesPage(state, { selected = it; linkError = null }, { progressId = it }, model::confirm, explore, settings, notify) { model.apply(it, false) }
        else -> ActivityListPage(state, { selected = it; linkError = null }, model::search, model::reload, model::refresh, model::loadMore, model::toggle, model::retryProgram, model::setOpenOnly, notify, model::clearRefreshFailure, settings)
    }
    if (sharing && activity?.officialUrl != null) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("활동 링크") }, text = { Text(activity.officialUrl) }, confirmButton = { TextButton({ sharing = false }) { Text("닫기") } })
    val openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }
    if (calendar && activity != null) CalendarConflictFlow(model.schedules(activity.id), openPrivacy) { calendar = false }
    stageCalendar?.let { stage -> progress?.let { model.stageWindow(it.id, stage) } }?.let { window -> CalendarConflictFlow(listOf(window), openPrivacy) { stageCalendar = null } }
    if (prompt && activity != null) ApplicationReportDialog({ model.apply(activity.id, it); prompt = false }, { prompt = false })
}
