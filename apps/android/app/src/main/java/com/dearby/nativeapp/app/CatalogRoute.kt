package com.dearby.nativeapp.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.application.ApplicationBrowser
import com.dearby.nativeapp.features.application.safeWebUrl
import com.dearby.nativeapp.pages.catalog.CatalogPage
import com.dearby.nativeapp.pages.catalog.ActivityDetailPage
import com.dearby.nativeapp.pages.catalog.ApplicationReportDialog

@Composable fun CatalogRoute(model: CatalogViewModel, saved: Boolean, active: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable(saved) { mutableStateOf<String?>(null) }
    var browser by rememberSaveable { mutableStateOf<String?>(null) }
    var prompt by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(selected, browser) { active(selected != null || browser != null); onDispose { active(false) } }
    val context = LocalContext.current
    val activity = state.activities.find { it.id == selected }
    BackHandler(selected != null && browser == null && !prompt) { selected = null }
    when {
        browser != null -> ApplicationBrowser(browser!!) { browser = null; prompt = true }
        activity != null -> ActivityDetailPage(activity, state.writing, state.storageReady, state.storageError ?: linkError, { selected = null }, { model.toggleProgram(activity.programId) }, { model.toggleOrganization(activity.organizationId) }, {
            runCatching { require(safeWebUrl(activity.source)); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.source))) }
                .onFailure { linkError = "공식 출처를 열지 못했습니다." }
        }, { activity.application?.let { if (safeWebUrl(it)) browser = it else linkError = "신청 주소를 확인할 수 없습니다." } }, { prompt = true })
        else -> CatalogPage(state, saved, model::refresh, { selected = it }, { if (it.organization) model.toggleOrganization(it.id) else model.toggleProgram(it.id) }, model::toggleProgram)
    }
    if (prompt && activity != null) ApplicationReportDialog(state.writing || !state.storageReady, state.storageError, { value -> model.report(activity.id, value) { prompt = false } }, { if (!state.writing) prompt = false })
}
