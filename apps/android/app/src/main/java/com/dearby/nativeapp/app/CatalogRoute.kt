package com.dearby.nativeapp.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.calendar.CalendarConflictSheet
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
    var linkError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (state.phase == CatalogPhase.LOADING && state.activities.isEmpty()) model.load() }
    DisposableEffect(selected) { active(selected != null); onDispose { active(false) } }
    val context = LocalContext.current
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
    if (calendar && activity != null) CalendarConflictSheet(model.schedules(activity.id)) { calendar = false }
    if (prompt && activity != null) ApplicationReportDialog({ model.apply(activity.id, it); prompt = false }, { prompt = false })
}
