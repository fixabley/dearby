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
import com.dearby.nativeapp.features.analytics.Analytics
import com.dearby.nativeapp.features.calendar.CalendarConflictFlow
import com.dearby.nativeapp.features.calendar.openCalendarInsert
import com.dearby.nativeapp.pages.activity.ActivityState
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.pages.activity.ActivityListPage
import com.dearby.nativeapp.pages.activity.ActivityDetailPage
import com.dearby.nativeapp.pages.activity.ActivityProgressPage
import com.dearby.nativeapp.pages.activity.ApplicationReportDialog
import com.dearby.nativeapp.pages.activity.MyActivitiesPage
import com.dearby.nativeapp.pages.activity.FavoriteHearts
import com.dearby.nativeapp.pages.activity.FavoritesPage
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.shared.ui.TabActions
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * [settings] and [notify] are the fixed 설정·알림 header buttons of both tab roots (contract "설정"). [openInMine] asks the shell to show an activity's
 * detail on the 내 활동 tab (contract "내 활동 직접 추가" → 내 활동에서 보기); [open] is that request arriving on the 내 활동 instance, consumed once.
 */
@Composable fun ActivityRoute(
    model: ActivityViewModel, mine: Boolean = false, explore: () -> Unit = {}, settings: () -> Unit = {}, notify: () -> Unit = {},
    openInMine: (String) -> Unit = {}, open: String? = null, consumeOpen: () -> Unit = {}, openConsent: () -> Unit = {}, header: (@Composable () -> Unit)? = null,
    hearts: FavoriteHearts = FavoriteHearts(), favorites: FavoritesViewModel? = null, segment: MineSegment = MineSegment.MINE, selectSegment: (MineSegment) -> Unit = {},
    signIn: () -> Unit = {}, unread: Int = 0, active: (Boolean) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    // `+` beside an activity heart (contract "`+` 내 활동에 추가"): adds it to 내 활동, or opens it there once it is in.
    var selected by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf<String?>(null) }
    // analytics-v1 `activity_view.source` of the open detail, reused by its `myactivity_add`·`apply_click`.
    var detailSource by remember { mutableStateOf("discovery") }
    fun show(id: String, source: String) {
        selected = id; linkError = null; detailSource = source
        Analytics.event("activity_view", *model.track(id), "source" to source)
    }
    val listSource = if (!mine) "discovery" else if (segment == MineSegment.FAVORITES) "favorites" else "my_activity"
    val plus: (String) -> Unit = { id -> if (state.known[id]?.applied == true) openInMine(id) else model.apply(id, true, listSource) }
    // 내 활동: the activity whose stage timeline is open, and the stage whose calendar comparison is open.
    var progressId by remember { mutableStateOf<String?>(null) }
    var stageCalendar by remember { mutableStateOf<Int?>(null) }
    var prompt by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    LaunchedEffect(open) { open?.let { show(it, model.takeLinkSource() ?: "my_activity"); consumeOpen() } }
    // Signed in, opening 내 활동 re-reads the account's records (and retries a failed import or read).
    LaunchedEffect(mine) { if (mine) model.syncMine(announce = true) }
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
                activity?.takeIf { it.applyUrl == url }?.let { Analytics.event("apply_click", *model.track(it.id), "source" to detailSource); model.openedApplication(it.id) }
            }
            .onFailure { linkError = "링크를 열지 못했어요." }
    }
    // 활동 공유 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): the Dearby activity page, which opens the app where it is installed,
    // through the system share sheet; the official page stays in the detail's 출처 row.
    val share = {
        activity?.let {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, activityLink(it.id))
                .putExtra(Intent.EXTRA_SUBJECT, it.title).putExtra(Intent.EXTRA_TITLE, it.title)
            context.startActivity(Intent.createChooser(send, null))
        }
        Unit
    }
    // Contract "활동 장소": the curated place opens in a map app pinned at its coordinates; no map app leaves the error on screen.
    val openMap = {
        activity?.place?.let { place ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.name)})".toUri())) }
                .onFailure { linkError = "지도 앱을 열지 못했어요." }
        }
        Unit
    }
    // 캘린더에 추가 (contract "내 캘린더에 추가"): the calendar app's prefilled add screen with the place and the official link; no app → a snackbar.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun addToCalendar(draft: CalendarDraft, of: ActivityState?) {
        val location = of?.place?.let { "${it.name}, ${it.address}" } ?: of?.location
        // The Dearby link first (contract "반복 일정과 겹침 결과 묶음"): the overlap check leaves out the events that carry it.
        val notes = listOfNotNull(of?.let { activityLink(it.id) }, of?.officialUrl).joinToString("\n").ifBlank { null }
        if (!openCalendarInsert(context, draft.title, draft.begin, draft.end, draft.allDay, location, notes, draft.recurrence)) scope.launch { snackbar.showSnackbar("캘린더 앱을 찾지 못했어요") }
    }
    // Detail over the timeline: back from the detail returns to the timeline it was opened from.
    BackHandler((selected != null || progressId != null) && !prompt && !calendar && stageCalendar == null) { if (selected != null) selected = null else progressId = null }
    Box(Modifier.fillMaxSize()) { when {
        activity != null -> ActivityDetailPage(activity, linkError, { selected = null }, openLink, { prompt = true },
            { calendar = true }, share, openMap,
            addToMine = { model.apply(activity.id, true, detailSource) }, viewInMine = { openInMine(activity.id) }, addToCalendar = { addToCalendar(it, activity) }, hearts = hearts)
        progress != null && progressActivity != null -> ActivityProgressPage(progress, { progressId = null }, { show(progress.id, "my_activity") },
            { stage, value -> model.mark(progress.id, stage, value) }, { stageCalendar = it }, { model.apply(progress.id, false); progressId = null }, { addToCalendar(it, progressActivity) })
        mine -> MineTab(segment, selectSegment, settings, notify, unread) {
            val openDetail: (String) -> Unit = { show(it, listSource) }
            if (segment == MineSegment.MINE || favorites == null) MyActivitiesPage(state, openDetail, { progressId = it }, explore) { model.apply(it, false) }
            else {
                val favoritesState by favorites.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { favorites.load() }
                FavoritesPage(favoritesState, state, hearts, openDetail, plus, { favorites.seen(it); model.ensureProgram(it) }, model::retryProgram,
                    signIn, favorites::load)
            }
        }
        else -> ActivityListPage(state, { show(it, "discovery") }, model::search, model::reload, model::refresh, model::loadMore, model::toggle, model::retryProgram, model::setOpenOnly, notify, model::clearRefreshFailure, settings, header, hearts, plus, unread)
    }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    val openPrivacy = { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }
    if (calendar && activity != null) CalendarConflictFlow(model.schedules(activity.id), activityLink(activity.id), openPrivacy) { calendar = false }
    progress?.let { of -> stageCalendar?.let { model.stageWindows(of.id, it) }?.takeIf { it.isNotEmpty() }?.let { windows -> CalendarConflictFlow(windows, activityLink(of.id), openPrivacy) { stageCalendar = null } } }
    // A failed 내 활동 read or write while signed in (contract "내 활동 서버 저장"), on whichever tab made it (only one tab's route exists at a time).
    // 403 CONSENT_REQUIRED on a 내 활동 write (consent version moved to 2026-10-22): go to 내 프로필, whose consent screen comes up on load.
    if (state.consentNeeded) AlertDialog(onDismissRequest = model::clearMineNotice, title = { Text("다시 동의가 필요해요") },
        text = { Text("개인정보 처리방침이 바뀌어 내 활동을 저장하려면 다시 동의해야 해요.") },
        confirmButton = { TextButton({ model.clearMineNotice(); openConsent() }) { Text("동의하러 가기") } }, dismissButton = { TextButton(model::clearMineNotice) { Text("나중에") } })
    else state.mineNotice?.let { value -> AlertDialog(onDismissRequest = model::clearMineNotice, title = { Text("내 활동") }, text = { Text(value) }, confirmButton = { TextButton(model::clearMineNotice) { Text("확인") } }) }
    if (prompt && activity != null) ApplicationReportDialog({ model.apply(activity.id, it, detailSource); prompt = false }, { prompt = false })
}

/** The 내 활동 tab's two top tabs (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25). */
enum class MineSegment(val label: String) { MINE("내 활동"), FAVORITES("즐겨찾기") }

/** One pinned top bar (설정·알림) over a Material 3 `PrimaryTabRow` `내 활동 | 즐겨찾기`, like the 명함 tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MineTab(segment: MineSegment, select: (MineSegment) -> Unit, settings: () -> Unit, notify: () -> Unit, unread: Int, content: @Composable () -> Unit) {
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection)) {
        DearbyTopBar("내 활동", scrollBehavior = topBar) { TabActions(settings, notify, unread = unread) }
        PrimaryTabRow(segment.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            MineSegment.entries.forEach { Tab(segment == it, { select(it) }, text = { Text(it.label) }) }
        }
        content()
    }
}

/**
 * The activity's web page, written into calendar notes so the overlap check can recognise Dearby's own events (contract "반복 일정과 겹침 결과 묶음"),
 * and what 활동 공유 sends (contract "유니버설 링크·App Links·활동 공유·오픈그래프").
 */
private fun activityLink(id: String) = "${BuildConfig.WEB_ORIGIN.trimEnd('/')}/activities/$id"
