package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.features.analytics.Analytics
import com.dearby.nativeapp.entities.activity.model.lines
import com.dearby.nativeapp.entities.activity.model.ActivityPage
import com.dearby.nativeapp.entities.activity.model.ProgramPage
import com.dearby.nativeapp.entities.activity.model.ProgramSummary
import com.dearby.nativeapp.entities.activity.model.currentStage
import com.dearby.nativeapp.entities.activity.model.instant
import com.dearby.nativeapp.entities.activity.model.safeHttpsUrl
import com.dearby.nativeapp.entities.activity.model.scheduleText
import com.dearby.nativeapp.entities.activity.model.rangeText
import com.dearby.nativeapp.entities.activity.model.deadlineCount
import com.dearby.nativeapp.entities.activity.model.stageBadge
import com.dearby.nativeapp.entities.activity.model.stages
import com.dearby.nativeapp.pages.activity.ActivityState
import com.dearby.nativeapp.pages.activity.ActivityListPhase
import com.dearby.nativeapp.pages.activity.ActivityListState
import com.dearby.nativeapp.pages.activity.DiscoveryKind
import com.dearby.nativeapp.pages.activity.StatusGroup
import com.dearby.nativeapp.pages.activity.ProgramActivities
import com.dearby.nativeapp.pages.activity.ProgramPhase
import com.dearby.nativeapp.pages.activity.ProgramState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStage
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStatus
import com.dearby.nativeapp.widgets.activity.progress.StatusOption
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import com.dearby.nativeapp.pages.activity.ScheduleItemState
import com.dearby.nativeapp.entities.activity.model.Stage
import com.dearby.nativeapp.entities.activity.model.Tag
import com.dearby.nativeapp.shared.ui.TagLabel
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.MyActivityRecord
import com.dearby.nativeapp.entities.account.model.MyScheduleStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discovery (contract "발견 프로그램 묶음"): GET /v1/programs page by page, and GET /v1/activities?programId= when a
 * program is expanded; a failure is an error state, never example activities. [fetchPrograms] takes the search text
 * (null for all), the cursor, the `status=` value (contract "활동 상태") and the kind; [fetch] takes the search text, the cursor, the program id,
 * the `status=` value and the kind.
 * Tags are a search condition only (contract "태그 칩 줄 제거", 2026-10-19 밤): no dictionary load, no tag filter.
 *
 * 내 활동 storage (contract "내 활동 서버 저장·일정 상태", 2026-10-22): signed out, the applied marks and stage statuses stay
 * in this view model's memory (the app's existing "device" storage — nothing is written to disk). Once [account] is signed in they
 * live on the server (`/v1/me/activities`): the memory records are imported once and cleared, every change is written through,
 * and a failed write shows [ActivityListState.mineNotice] and re-reads the server. [fetchOne] reads an activity discovery has not
 * loaded (`GET /v1/activities/:id`, null when it is gone → "삭제된 활동"). Signing out drops the account's records from memory.
 */
class ActivityViewModel(
    private val fetchPrograms: suspend (String?, String?, String, String, String?) -> ProgramPage, // query, cursor, status, kind, category
    private val fetch: suspend (String?, String?, String?, String, String?, String?) -> ActivityPage, // query, cursor, programId, status, kind, category
    private val fetchOne: suspend (String) -> ActivityModel? = { null },
    private val account: AccountViewModel? = null,
    /** 발견 `프로그램 | 이벤트` remembered on the device (2026-10-26): the starting choice and where a change is saved. */
    kind: DiscoveryKind = DiscoveryKind.PROGRAM, private val saveKind: (DiscoveryKind) -> Unit = {},
    /** The 상태 dropdown remembered on the device (2026-10-26), like [kind]. */
    statusGroups: Set<StatusGroup> = StatusGroup.DEFAULT, private val saveStatusGroups: (Set<StatusGroup>) -> Unit = {},
    /** The 종류 dropdown remembered on the device per kind (2026-10-26): read when the kind changes, saved on each change. */
    private val loadCategories: (DiscoveryKind) -> Set<String> = { emptySet() }, private val saveCategories: (DiscoveryKind, Set<String>) -> Unit = { _, _ -> },
) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityListState(kind = kind, statusGroups = statusGroups, categories = loadCategories(kind)))
    val state = mutable.asStateFlow()
    private var models: Map<String, ActivityModel> = emptyMap()
    // The user's own stage statuses by activity id, then schedule id (contract "내 활동 서버 저장·일정 상태").
    private var marks: Map<String, Map<String, Choice>> = emptyMap()
    // True while the 내 활동 records live on the server (signed in); [importing] holds memory records not uploaded yet.
    private var serverMode = false
    private var importing: List<MyActivityRecord> = emptyList()
    private var syncJob: Job? = null
    // Writes go one at a time so an add followed by a quick remove reaches the server in that order.
    private val writes = Mutex()
    // One program request at a time: a new search cancels the previous search, page and program loads, so late answers are dropped.
    private var job: Job? = null
    private val programJobs = mutableMapOf<String, Job>()
    private var opened: String? = null
    private var leftApp = false
    private val neverAsk = mutableSetOf<String>()

    init {
        reload()
        account?.let { account ->
            viewModelScope.launch {
                // Server storage only once signed in with the consent given ([AccountViewModel.ready], contract "개인정보 동의" 2026-10-25).
                account.ready.map { if (it) AccountPhase.SIGNED_IN else AccountPhase.SIGNED_OUT }.collect { phase ->
                    if (phase == AccountPhase.SIGNED_IN && !serverMode) { serverMode = true; importing = deviceRecords(); mutable.update { it.copy(mineLoading = true) }; syncMine() }
                    else if (phase == AccountPhase.SIGNED_OUT && serverMode) signedOut()
                }
            }
        }
    }

    /** Typing: show the text now, ask the server once it has rested for 300 ms. */
    fun search(text: String) {
        if (text == state.value.query) return
        mutable.update { it.copy(query = text) }
        start(debounce = true)
    }
    fun reload() = start(debounce = false)
    /**
     * The 상태 dropdown (contract "활동 상태", 2026-10-26): the list and expanded programs are read again with those states. Unchecking the
     * last group falls back to 지원 가능.
     */
    fun setStatusGroups(value: Set<StatusGroup>) {
        val groups = value.ifEmpty { StatusGroup.DEFAULT }
        if (groups == state.value.statusGroups) return
        mutable.update { it.copy(statusGroups = groups) }
        saveStatusGroups(groups)
        Analytics.event("discovery_status_filter", "groups" to StatusGroup.codes(groups))
        start(debounce = false)
    }
    /** The 종류 dropdown (2026-10-26 사용자 요청): the list and expanded programs are read again with `category=`; nothing chosen is 전체. */
    fun setCategories(value: Set<String>) {
        if (value == state.value.categories) return
        val kind = state.value.kind
        mutable.update { it.copy(categories = value) }
        saveCategories(kind, value)
        Analytics.event("discovery_category_filter", "categories" to kind.chosen(value).joinToString(",") { it.code.lowercase() })
        start(debounce = false)
    }
    /** 발견 `프로그램 | 이벤트` (contract "프로그램·이벤트 전환"): the list and expanded programs are read again for that kind and its kept 종류. */
    fun setKind(value: DiscoveryKind) {
        if (value == state.value.kind) return
        mutable.update { it.copy(kind = value, categories = loadCategories(value)) }
        saveKind(value)
        Analytics.event("discovery_mode", "mode" to value.code)
        start(debounce = false)
    }
    private fun start(debounce: Boolean) {
        job?.cancel()
        programJobs.values.forEach { it.cancel() }
        programJobs.clear()
        job = viewModelScope.launch {
            if (debounce) delay(300)
            mutable.update { it.copy(phase = ActivityListPhase.LOADING, programs = emptyList(), expanded = emptySet(), programActivities = emptyMap(), nextCursor = null, loadingMore = false, moreFailed = false, refreshing = false, refreshFailed = false) }
            val page = programPage(null) ?: return@launch mutable.update { it.copy(phase = ActivityListPhase.FAILED) }
            show(page, append = false)
        }
    }
    /**
     * Pull to refresh (contract "발견 탭 스크롤 동작"): the first program page again while the list stays on screen; which programs are
     * expanded is kept and their activities are read again. A failure leaves the list as it was and flags [ActivityListState.refreshFailed]
     * (the error screen only when nothing was listed).
     */
    fun refresh() {
        if (state.value.phase != ActivityListPhase.LOADED) return reload()
        if (state.value.refreshing) return
        job?.cancel()
        programJobs.values.forEach { it.cancel() }
        programJobs.clear()
        job = viewModelScope.launch {
            mutable.update { it.copy(refreshing = true, loadingMore = false, moreFailed = false, refreshFailed = false) }
            val page = programPage(null) ?: return@launch mutable.update {
                if (it.programs.isEmpty()) it.copy(phase = ActivityListPhase.FAILED, refreshing = false) else it.copy(refreshing = false, refreshFailed = true)
            }
            // Programs that fell off the first page lose their expansion, or they would come back via loadMore expanded but never loaded.
            val listed = page.items.map { it.id }.toSet()
            mutable.update { it.copy(programActivities = emptyMap(), expanded = it.expanded.filter { id -> id in listed }.toSet()) }
            show(page, append = false)
            state.value.expanded.forEach(::loadProgram)
            mutable.update { it.copy(refreshing = false) }
        }
    }
    /** The refresh-failure notice was shown; otherwise the flag would show it again whenever the page comes back. */
    fun clearRefreshFailure() = mutable.update { it.copy(refreshFailed = false) }
    /** The list is near its end: fetch the next program page, if there is one and nothing else is loading. */
    fun loadMore() {
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.phase != ActivityListPhase.LOADED || current.loadingMore || job?.isActive == true) return
        job = viewModelScope.launch {
            mutable.update { it.copy(loadingMore = true, moreFailed = false) }
            val page = programPage(cursor) ?: return@launch mutable.update { it.copy(loadingMore = false, moreFailed = true) }
            show(page, append = true)
        }
    }
    private suspend fun programPage(cursor: String?): ProgramPage? = try {
        fetchPrograms(state.value.query.trim().ifEmpty { null }, cursor, StatusGroup.query(state.value.statusGroups), state.value.kind.code, state.value.kind.categoryQuery(state.value.categories))
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        // Only the origin and the failure kind; the search text is not logged.
        Log.w("Dearby", "Programs load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
        null
    }
    /** Programs never start expanded (contract "자동 펼침 없음"); only a tap on a card expands it. */
    private fun show(page: ProgramPage, append: Boolean) {
        val programs = page.items.map { it.toState() }
        mutable.update { state ->
            state.copy(phase = ActivityListPhase.LOADED, programs = (if (append) state.programs else emptyList()) + programs,
                nextCursor = page.nextCursor, loadingMore = false)
        }
    }
    /** Expand or collapse a program; the first expand loads its open activities. */
    fun toggle(programId: String) {
        val expanded = programId in state.value.expanded
        mutable.update { it.copy(expanded = if (expanded) it.expanded - programId else it.expanded + programId) }
        if (!expanded && state.value.programActivities[programId]?.phase != ProgramPhase.LOADED) loadProgram(programId)
    }
    fun retryProgram(programId: String) = loadProgram(programId)
    private fun loadProgram(programId: String) {
        if (programJobs[programId]?.isActive == true) return
        mutable.update { it.copy(programActivities = it.programActivities + (programId to ProgramActivities())) }
        val kind = state.value.kind.code
        val status = StatusGroup.query(state.value.statusGroups)
        val category = state.value.kind.categoryQuery(state.value.categories)
        programJobs[programId] = viewModelScope.launch {
            val items = mutableListOf<ActivityModel>()
            var cursor: String? = null
            try {
                // Every page of the program's activities in the chosen states (contract "활동 상태": the server decides each state).
                for (attempt in 1..MAX_PROGRAM_PAGES) {
                    val page = fetch(null, cursor, programId, status, kind, category)
                    items += page.items
                    // `break`, not `return@repeat`: the latter only skips to the next pass, which fetched page 1 again (each activity shown 5 times).
                    cursor = page.nextCursor ?: break
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Program activities load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch mutable.update { it.copy(programActivities = it.programActivities + (programId to ProgramActivities(ProgramPhase.FAILED))) }
            }
            val now = Instant.now()
            // Split by the server's state: 모집 중 (category groups on the page), 모집 예정 nearest first, 진행 중, and the rest as 지난 활동 within
            // three years (an undated one stays), most recent start first.
            val byStatus = items.groupBy { it.status }
            val open = byStatus["RECRUITING"].orEmpty()
            val upcoming = byStatus["RECRUITING_UPCOMING"].orEmpty().sortedWith(compareBy(nullsLast()) { it.upcomingKey })
            val inProgress = byStatus["IN_PROGRESS"].orEmpty().sortedWith(compareByDescending(nullsFirst()) { it.activityStart })
            val since = now.minus(PAST_DAYS, java.time.temporal.ChronoUnit.DAYS)
            val past = items.filter { it.status in StatusGroup.PAST.statuses && it.activityStart?.let { start -> start >= since } != false }.sortedByDescending { it.activityStart }
            val shown = open + upcoming + inProgress + past
            models = models + shown.associateBy { it.id }
            val today = today()
            mutable.update { state ->
                val loaded = shown.associate { activity ->
                    activity.id to activity.toState(now).let { known -> state.known[known.id]?.takeUnless { it.deleted }?.let { known.copy(applied = it.applied) } ?: known }
                }
                state.copy(known = state.known + loaded, progress = state.progress + shown.associate { it.id to it.toProgress(marks[it.id].orEmpty(), today) },
                    programActivities = state.programActivities + (programId to ProgramActivities(ProgramPhase.LOADED, open.map { it.id }, upcoming.map { it.id }, inProgress.map { it.id }, past.map { it.id })))
            }
        }
    }
    fun schedules(id: String) = models[id]?.schedules.orEmpty()
    /** The calendar-comparison windows of one stage (contract "겹침 확인 시간"); empty for an uncomparable or unknown stage. */
    fun stageWindows(id: String, stage: Int) = models[id]?.stages()?.getOrNull(stage)?.windows.orEmpty()
    /** The user picks one of a stage's statuses; the track is rebuilt from today's date. Signed in, it is saved on the server too. */
    fun mark(id: String, stage: Int, option: StatusOption) {
        val model = models[id] ?: return
        val scheduleId = model.stages().getOrNull(stage)?.scheduleId ?: return
        val own = marks[id].orEmpty() + (scheduleId to Choice(option.id, option.kind))
        marks = marks + (id to own)
        mutable.update { it.copy(progress = it.progress + (id to model.toProgress(own, today()))) }
        write { account, session -> account.client.saveScheduleStatus(id, scheduleId, option.id, option.kind, session) }
    }
    /** Marks [id] applied. Removing it (contract "빼기는 눈에 보이게") also clears the stage statuses. */
    /** [source] is analytics-v1 `myactivity_add.source` (where the add was made). */
    fun apply(id: String, value: Boolean, source: String = "detail") {
        if ((state.value.known[id] ?: return).applied == value) return
        update(id) { it.copy(applied = value) }
        if (value) Analytics.event("myactivity_add", *track(id), "source" to source)
        if (value) write { account, session -> account.client.saveMyActivity(id, session) }
        else write { account, session -> account.client.deleteMyActivity(id, session) }
        if (value || id !in marks) return
        marks = marks - id
        models[id]?.let { model -> mutable.update { it.copy(progress = it.progress + (id to model.toProgress(emptyMap(), today()))) } }
    }
    /**
     * An App Link `/activities/<id>` (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): an activity discovery has not loaded is read
     * first (`GET /v1/activities/:id`), then [ActivityListState.linked] asks 발견 to open its detail; gone or unreadable → [ActivityListState.linkNotice].
     */
    /** analytics-v1 `field`·`category` (and the id under [idKey]) of a loaded activity. */
    fun track(id: String, idKey: String? = "activity_id") = Tracking.activity(models[id], id, idKey)
    /** Where the pending [openLinked] came from (`link`·`push`·`queue`·`inbox`), taken once by the detail it opens. */
    private var linkSource: String? = null
    fun takeLinkSource() = linkSource.also { linkSource = null }
    fun openLinked(id: String, source: String = "link") {
        linkSource = source
        if (id in models && state.value.known[id]?.deleted == false) return mutable.update { it.copy(linked = id) }
        viewModelScope.launch {
            val model = try { fetchOne(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Linked activity load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch mutable.update { it.copy(linkNotice = "활동을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.") }
            } ?: return@launch mutable.update { it.copy(linkNotice = "더 이상 볼 수 없는 활동이에요.") }
            models = models + (id to model)
            val shown = model.toState(Instant.now()).copy(applied = state.value.known[id]?.applied == true)
            mutable.update { it.copy(known = it.known + (id to shown), progress = it.progress + (id to model.toProgress(marks[id].orEmpty(), today())), linked = id) }
        }
    }
    /**
     * Activities read elsewhere (즐겨찾기, 맞춤 대기열) become known here, so their rows and details open and `+` can add them to 내 활동; an activity
     * already known keeps its 내 활동 mark.
     */
    fun adopt(loaded: List<ActivityModel>) {
        if (loaded.isEmpty()) return
        models = models + loaded.associateBy { it.id }
        val now = Instant.now()
        val today = today()
        mutable.update { state ->
            state.copy(known = state.known + loaded.associate { model -> model.id to model.toState(now).copy(applied = state.known[model.id]?.applied == true) },
                progress = state.progress + loaded.associate { it.id to it.toProgress(marks[it.id].orEmpty(), today) })
        }
    }
    /** 즐겨찾기 expanded a program card: its activities load once, as in discovery. */
    fun ensureProgram(programId: String) { if (state.value.programActivities[programId]?.phase != ProgramPhase.LOADED) loadProgram(programId) }
    fun consumeLinked() = mutable.update { it.copy(linked = null, linkNotice = null) }
    /** An application link for [id] was handed to the browser. Applied or "never ask" activities are not asked. */
    fun openedApplication(id: String) {
        if (state.value.known[id]?.applied == true || id in neverAsk) return
        opened = id
        leftApp = false
    }
    /** The app went to the background, e.g. because the browser opened. */
    fun appLeft() { if (opened != null) leftApp = true }
    /** The app is in front again: ask about the last opened application, once. */
    fun appReturned() {
        val id = opened?.takeIf { leftApp } ?: return
        opened = null
        leftApp = false
        mutable.update { it.copy(askingId = id) }
    }
    enum class ApplyAnswer { APPLIED, NOT_YET, NEVER_ASK }
    fun answer(answer: ApplyAnswer) {
        val id = state.value.askingId ?: return
        mutable.update { it.copy(askingId = null) }
        when (answer) {
            ApplyAnswer.APPLIED -> apply(id, true, "apply_prompt")
            ApplyAnswer.NOT_YET -> Unit
            ApplyAnswer.NEVER_ASK -> neverAsk += id
        }
    }
    private fun update(id: String, change: (ActivityState) -> ActivityState) {
        mutable.update { state -> state.known[id]?.let { state.copy(known = state.known + (id to change(it))) } ?: state }
    }
    fun clearMineNotice() = mutable.update { it.copy(mineNotice = null, consentNeeded = false) }

    /** Signed in: one write to `/v1/me/activities…`. A failure tells the user and re-reads the server, which undoes the local change. */
    private fun write(call: suspend (AccountViewModel, AccountSession) -> Unit) {
        val account = account?.takeIf { serverMode } ?: return
        viewModelScope.launch {
            try { writes.withLock { account.authorized { call(account, it) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // 401 after a refused refresh signs out, which clears the records anyway.
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                val consent = e is AccountException && e.error == AccountError.FORBIDDEN && e.code == "CONSENT_REQUIRED"
                mutable.update { it.copy(mineNotice = writeFailure(e), consentNeeded = consent) }
                syncMine()
            }
        }
    }
    /**
     * Reads the account's records (importing the memory records first, once, right after sign-in) and shows them; activities
     * discovery has not loaded are read one by one, a gone one becomes a "삭제된 활동" row. Also called when 내 활동 opens; only that
     * call ([announce]) tells the user about a failed read, so an offline start stays quiet on 발견.
     */
    fun syncMine(announce: Boolean = false) {
        val account = account?.takeIf { serverMode } ?: return
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            try {
                val upload = importing
                val records = writes.withLock {
                    if (upload.isEmpty()) account.authorized { account.client.myActivities(it) }
                    else account.authorized { account.client.importMyActivities(upload, it) }.also { importing = emptyList() }
                }
                val missing = records.filter { !it.deleted && it.activityId !in models }.map { it.activityId }
                val loaded = missing.map { id -> async { id to fetchOne(id) } }.awaitAll()
                if (!serverMode) return@launch
                showServer(records, loaded.mapNotNull { (id, model) -> model?.let { id to it } }.toMap())
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                Log.w("Dearby", "My activities sync with ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                if (announce) mutable.update { it.copy(mineNotice = "내 활동을 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요.") }
            } finally { mutable.update { it.copy(mineLoading = false) } }
        }
    }
    /** The server's records replace the memory ones: applied and stage statuses; unknown ids become "삭제된 활동". */
    private fun showServer(records: List<MyActivityRecord>, loaded: Map<String, ActivityModel>) {
        models = models + loaded
        val byId = records.associateBy { it.activityId }
        marks = records.associate { record -> record.activityId to record.statuses.associate { it.scheduleId to Choice(it.statusId, it.kind) } }
        val now = Instant.now()
        val today = today()
        mutable.update { state ->
            val known = state.known.filterValues { !it.deleted } + loaded.mapValues { (_, model) -> model.toState(now) }
            val kept = known.filterKeys { byId[it]?.deleted != true }.mapValues { (id, activity) -> activity.copy(applied = id in byId) }
            val gone = byId.keys.filter { it !in kept }.associateWith { deletedActivity(it) }
            state.copy(known = kept + gone, progress = models.mapValues { (id, model) -> model.toProgress(marks[id].orEmpty(), today) })
        }
    }
    private fun signedOut() {
        serverMode = false
        syncJob?.cancel()
        importing = emptyList()
        marks = emptyMap()
        val today = today()
        mutable.update { state ->
            state.copy(known = state.known.filterValues { !it.deleted }.mapValues { it.value.copy(applied = false) }, mineLoading = false,
                progress = models.mapValues { (_, model) -> model.toProgress(emptyMap(), today) })
        }
    }
    /** The memory records to import: applied activities with their default-kind statuses (the import takes `{scheduleId, kind}`). */
    private fun deviceRecords(): List<MyActivityRecord> = state.value.known.values.filter { it.applied && !it.deleted }.map { activity ->
        MyActivityRecord(activity.id, marks[activity.id].orEmpty().map { (scheduleId, choice) -> MyScheduleStatus(scheduleId, null, choice.kind) })
    }
}

/** A stage's status choice: an admin status by [statusId], or a default one (null id) by [kind]. */
private data class Choice(val statusId: String?, val kind: String)
private fun writeFailure(e: Exception) = when {
    e is AccountException && e.error == AccountError.NOT_FOUND -> "더 이상 볼 수 없는 활동이라 내 활동에 저장하지 못했어요."
    else -> "내 활동을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요."
}
/** A 내 활동 record whose activity is gone (contract "내 활동 서버 저장": logical FK): only 빼기 is offered. */
private fun deletedActivity(id: String) = ActivityState(id, "삭제된 활동", "", "", "", "", "", null, null, emptyList(), null, null, null, null, null,
    applied = true, deleted = true)

private const val MAX_PROGRAM_PAGES = 5
/** 지난 활동 window: the last three years (2026-10-19 저녁, 2년 → 3년). */
private const val PAST_DAYS = 1095L
private fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
/** The system "add event" prefill for one stage (contract "내 캘린더에 추가"): "활동명 · 일정 제목"; null for an undated stage. */
private fun ActivityModel.draft(stage: Stage): CalendarDraft? {
    val start = stage.eventStart ?: return null
    return CalendarDraft("$name · ${if (stage.kind == "APPLICATION") "모집 마감" else stage.name}", start.toEpochMilli(), (stage.eventEnd ?: start).toEpochMilli(), stage.allDay, stage.recurrence)
}

private fun List<Tag>.labels() = map { TagLabel(it.name, it.nameEn) }
/** One program card's state, for discovery and 즐겨찾기 alike. */
internal fun ProgramSummary.toState() = ProgramState(id, name, organization, openActivityCount, instant(nearestRecruitmentEndAt), matchedActivityIds.size, instant(latestActivityAt),
    fieldTags = fieldTags.labels(), traitTags = traitTags.labels(), upcomingCount = upcomingActivityCount, nearestStart = instant(nearestRecruitmentStartAt), logoUrl = logoUrl,
    deadlineIsEvent = nearestIsActivityStart, hasTimetable = hasTimetable, statusCounts = statusCounts, category = category?.label, categoryCode = category?.name)

private fun ActivityModel.toState(now: Instant): ActivityState {
    val status = statusLabel(now)
    // 모집 날짜 표기 (user 2026-10-26): a window with both ends is a range plus a D-day badge, without a separate `모집 마감` line.
    val range = application?.rangeText()
    val stages = stages()
    val deadline = stages.firstOrNull { it.kind == "APPLICATION" }?.takeIf { range != null }
    return ActivityState(
        // The activity period by the structured-first rule (contract "일정 표시 우선순위"); the collected label only when no date exists.
        id, name, description, organization, program, status, activitySchedule?.scheduleText()?.ifBlank { null } ?: dateLabel, venue, safeHttpsUrl(imageUrl),
        stages.filterNot { it === deadline }.map { ScheduleItemState(if (it.kind == "APPLICATION") "모집 마감" else it.name, it.dateText, draft(it), it.scheduleId, it.hasTimetable, it.selection && !it.hasTimetable) },
        safeHttpsUrl(officialUrl), applyUrl(now), range ?: period(), recruitmentEnd, schedules.firstOrNull()?.startAt,
        eligibility = eligibility?.lines() ?: emptyList(),
        category = category?.label, categoryOrder = category?.ordinal ?: Int.MAX_VALUE, categoryCode = category?.name,
        sortKey = sortKey,
        fieldTags = fieldTags.labels(), fieldTagsAll = fieldTagsAll.labels(), traitTags = traitTags.labels(),
        place = place,
        applicationUrl = safeHttpsUrl(applicationUrl),
        overlapCheckable = schedules.isNotEmpty(),
        hasTimetable = (listOfNotNull(activitySchedule) + stageSchedules).any { it.hasTimetable },
        periodDetail = application?.rangeText(withTime = true), deadlineCount = application?.takeIf { status == "모집 중" }?.deadlineCount(),
        deadlineDraft = deadline?.let { draft(it) },
        noRecruitment = this.status == "NO_RECRUITMENT",
    )
}
/**
 * The stage track with the user's [marks] (by schedule id): a PASSED status makes the stage past, the earliest other stage not
 * over by [today] is current, a FAILED status or no current stage ends the activity. No stages at all leaves an empty track.
 * A stage without a choice shows its first waiting status; a stage that is not a 선발 단계 has no options, so an old mark on it is ignored.
 */
private fun ActivityModel.toProgress(marks: Map<String, Choice>, today: LocalDate): ProgressState {
    val stages = stages()
    val options = stages.map { stage -> stage.statuses.map { StatusOption(it.id, it.name, it.kind) } }
    // The user's explicit choice per stage: the admin status by id, else the default status of that kind; null when none was made.
    val picks = stages.mapIndexed { index, stage ->
        marks[stage.scheduleId]?.let { pick -> options[index].firstOrNull { pick.statusId != null && it.id == pick.statusId } ?: options[index].firstOrNull { it.kind == pick.kind } }
    }
    val passed = picks.indices.filter { picks[it]?.passed == true }.toSet()
    val failedAt = picks.indices.firstOrNull { picks[it]?.failed == true }
    val current = if (failedAt == null) currentStage(stages, passed, today) else null
    val ended = stages.isNotEmpty() && (failedAt != null || current == null)
    val shown = stages.mapIndexed { index, stage ->
        val status = when {
            index == current -> ProgressStatus.CURRENT
            index in passed || stage.finished(today) || index <= (failedAt ?: (current ?: 0) - 1) -> ProgressStatus.PAST
            else -> ProgressStatus.UPCOMING
        }
        ProgressStage(stage.name, stage.kind, stage.dateText, status, options[index], picks[index] ?: options[index].firstOrNull { it.kind == "WAITING" }, stage.windows.isNotEmpty(), draft(stage),
            stage.scheduleId?.takeIf { stage.hasTimetable }, stage.selection)
    }
    // 참여 중 (2026-10-22, 참여 확정 replaced): every 선발 단계 (contract "선발 과정·활동 일정 분리": `phase`) is marked PASSED, none FAILED, and a step is still ahead.
    val selection = stages.indices.filter { stages[it].selection }.map { picks[it] }
    val participating = failedAt == null && current != null && selection.isNotEmpty() && selection.all { it?.passed == true }
    val badge = when { stages.isEmpty() -> ""; failedAt != null -> shown[failedAt].selected?.name ?: "탈락"; current == null -> "지남"; participating -> "참여 중"; else -> stageBadge(stages[current], today) }
    return ProgressState(id, name, "$program · $organization", shown, current, badge, ended, failedAt != null, current?.let { stages[it].day }, participating)
}

/** The recruitment window as text (contract "일정 표시 우선순위" format: `10월 1일 (수) 14:07 ~ 10월 8일 (수) 00:00`, end only `~ … 마감`), null without one. */
private fun ActivityModel.period(): String? = application?.let { scheduleText(it.startAt, it.endAt, it.startDate, it.endDateExclusive, recruitment = true) }?.ifBlank { null }
