package com.dearby.nativeapp.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.application.ApplicationBrowser
import com.dearby.nativeapp.features.calendar.CalendarConflictSheet
import com.dearby.nativeapp.pages.catalog.CatalogPage
import com.dearby.nativeapp.pages.catalog.ActivityDetailPage
import com.dearby.nativeapp.pages.catalog.ApplicationReportDialog

@Composable fun CatalogRoute(model: CatalogViewModel, savedOnly: Boolean = false, active: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var browser by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(selected) { active(selected != null); onDispose { active(false) } }
    val context = LocalContext.current
    val activity = state.activities.find { it.id == selected }
    BackHandler(selected != null && !browser && !prompt && !calendar) { selected = null }
    when {
        browser && activity != null -> ApplicationBrowser(activity.source) { browser = false; prompt = true }
        activity != null -> ActivityDetailPage(activity, linkError, { selected = null }, {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, activity.source.toUri())) }
                .onFailure { linkError = "예시 링크를 열지 못했습니다." }
        }, { browser = true }, { prompt = true }, { calendar = true }, { model.save(activity.id) }, model::saveOrganization, { sharing = true })
        else -> CatalogPage(state, { selected = it; linkError = null }, model::filter, model::save, savedOnly)
    }
    if (sharing && activity != null) AlertDialog(onDismissRequest = { sharing = false }, title = { Text("활동 링크") }, text = { Text(activity.source + "\n예시 공유 화면입니다.") }, confirmButton = { TextButton({ sharing = false }) { Text("닫기") } })
    if (calendar && activity != null) CalendarConflictSheet(model.schedules(activity.id)) { calendar = false }
    if (prompt && activity != null) ApplicationReportDialog({ model.report(activity.id, it); prompt = false }, { prompt = false })
}
