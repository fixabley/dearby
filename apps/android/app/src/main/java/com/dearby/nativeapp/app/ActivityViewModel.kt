package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.lines
import com.dearby.nativeapp.entities.activity.model.ActivityPage
import com.dearby.nativeapp.entities.activity.model.ProgramPage
import com.dearby.nativeapp.entities.activity.model.Tag
import com.dearby.nativeapp.entities.activity.model.currentStage
import com.dearby.nativeapp.entities.activity.model.instant
import com.dearby.nativeapp.entities.activity.model.safeHttpsUrl
import com.dearby.nativeapp.entities.activity.model.stageBadge
import com.dearby.nativeapp.entities.activity.model.stages
import com.dearby.nativeapp.pages.activity.ActivityState
import com.dearby.nativeapp.pages.activity.ActivityListPhase
import com.dearby.nativeapp.pages.activity.ActivityListState
import com.dearby.nativeapp.pages.activity.ProgramActivities
import com.dearby.nativeapp.pages.activity.ProgramPhase
import com.dearby.nativeapp.pages.activity.ProgramState
import com.dearby.nativeapp.pages.activity.TagState
import com.dearby.nativeapp.widgets.activity.progress.ProgressMark
import com.dearby.nativeapp.widgets.activity.progress.ProgressStage
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import com.dearby.nativeapp.widgets.activity.progress.ProgressStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discovery (contract "발견 프로그램 묶음"): GET /v1/programs page by page, and GET /v1/activities?programId= when a
 * program is expanded; a failure is an error state, never example activities. [fetchPrograms] takes the search text
 * (null for all), the cursor, the open-only flag and the selected tag ids; [fetch] takes the search text, the cursor, the program id,
 * the open flag and the selected tag ids; [fetchTags] loads the tag dictionary once (contract "태그"; a failure just hides the chips).
 */
class ActivityViewModel(
    private val fetchPrograms: suspend (String?, String?, Boolean, List<String>) -> ProgramPage, // query, cursor, openOnly, tagIds
    private val fetch: suspend (String?, String?, String?, Boolean, List<String>) -> ActivityPage, // query, cursor, programId, open, tagIds
    private val fetchTags: suspend () -> List<Tag> = { emptyList() },
) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityListState())
    val state = mutable.asStateFlow()
    private var models: Map<String, ActivityModel> = emptyMap()
    // The user's own stage marks (contract "내 활동 진행 트랙"), memory only like the applied marks.
    private var marks: Map<String, Map<Int, ProgressMark>> = emptyMap()
    // One program request at a time: a new search cancels the previous search, page and program loads, so late answers are dropped.
    private var job: Job? = null
    private val programJobs = mutableMapOf<String, Job>()
    private var opened: String? = null
    private var leftApp = false
    private val neverAsk = mutableSetOf<String>()

    init {
        reload()
        viewModelScope.launch {
            val tags = try { fetchTags() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Tags load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}"); emptyList()
            }
            mutable.update { it.copy(tags = tags.map { tag -> TagState(tag.id, tag.name) }) }
        }
    }

    /** Typing: show the text now, ask the server once it has rested for 300 ms. */
    fun search(text: String) {
        if (text == state.value.query) return
        mutable.update { it.copy(query = text) }
        start(debounce = true)
    }
    fun reload() = start(debounce = false)
    /** The `마감 숨기기` switch (contract "모집 중인 프로그램만 보기"): off also lists programs active in the last three years and always shows their other activities when expanded. */
    fun setOpenOnly(value: Boolean) {
        if (value == state.value.openOnly) return
        mutable.update { it.copy(openOnly = value) }
        start(debounce = false)
    }
    /** A tag filter chip was tapped (contract "태그"): every chosen tag must be present; the list reloads at once. */
    fun toggleTag(id: String) {
        mutable.update { it.copy(selectedTagIds = if (id in it.selectedTagIds) it.selectedTagIds - id else it.selectedTagIds + id) }
        start(debounce = false)
    }
    private val tagIds get() = state.value.selectedTagIds.toList().sorted()
    private fun start(debounce: Boolean) {
        job?.cancel()
        programJobs.values.forEach { it.cancel() }
        programJobs.clear()
        job = viewModelScope.launch {
            if (debounce) delay(300)
            mutable.update { it.copy(phase = ActivityListPhase.LOADING, programs = emptyList(), expanded = emptySet(), programActivities = emptyMap(), nextCursor = null, loadingMore = false, moreFailed = false) }
            val page = programPage(null) ?: return@launch mutable.update { it.copy(phase = ActivityListPhase.FAILED) }
            show(page, append = false)
        }
    }
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
        fetchPrograms(state.value.query.trim().ifEmpty { null }, cursor, state.value.openOnly, tagIds)
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        // Only the origin and the failure kind; the search text is not logged.
        Log.w("Dearby", "Programs load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
        null
    }
    /** Programs with one open activity, or whose activity name matched the search, start expanded (contract rule; only while the switch is on). */
    private fun show(page: ProgramPage, append: Boolean) {
        val programs = page.items.map { ProgramState(it.id, it.name, it.organization, it.openActivityCount, instant(it.nearestRecruitmentEndAt), it.matchedActivityIds.isNotEmpty(), instant(it.latestActivityAt), it.tags.map { tag -> tag.name }) }
        val opening = if (state.value.openOnly) programs.filter { it.openCount == 1 || it.matched }.map { it.id } else emptyList()
        mutable.update { state ->
            state.copy(phase = ActivityListPhase.LOADED, programs = (if (append) state.programs else emptyList()) + programs,
                expanded = state.expanded + opening, nextCursor = page.nextCursor, loadingMore = false)
        }
        opening.forEach(::loadProgram)
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
        programJobs[programId] = viewModelScope.launch {
            val items = mutableListOf<ActivityModel>()
            var cursor: String? = null
            try {
                // Every page of the program's open activities (contract "열린 활동 판정 일원화": the server decides openness).
                repeat(MAX_PROGRAM_PAGES) {
                    val page = fetch(null, cursor, programId, true, tagIds)
                    items += page.items
                    cursor = page.nextCursor ?: return@repeat
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Program activities load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch mutable.update { it.copy(programActivities = it.programActivities + (programId to ProgramActivities(ProgramPhase.FAILED))) }
            }
            val now = Instant.now()
            val open = items
            // The program's other activities (contract "모집 중인 프로그램만 보기") in two groups — 모집 예정 (recruitment or activity start
            // still ahead, nearest first) and 지난 활동 (started within three years, most recent first). Shown always while the switch is
            // off (2026-10-19 저녁 결정), and only when nothing is open while it is on. Activities already shown as open are excluded.
            val others = if (open.isNotEmpty() && state.value.openOnly) emptyList() else try {
                fetch(null, null, programId, false, tagIds).items
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Program activities (fallback) load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                emptyList()
            }
            val openIds = open.map { it.id }.toSet()
            val upcoming = others.filter { it.id !in openIds && (it.upcomingKey ?: Instant.MIN) > now }.sortedBy { it.upcomingKey }
            val since = now.minus(PAST_DAYS, java.time.temporal.ChronoUnit.DAYS)
            val past = others.filter { it.id !in openIds && it !in upcoming && (it.activityStart ?: Instant.MIN) >= since }.sortedByDescending { it.activityStart }
            val shown = open + upcoming + past
            models = models + shown.associateBy { it.id }
            val today = today()
            mutable.update { state ->
                val loaded = shown.associate { activity ->
                    activity.id to activity.toState(now).let { known -> state.known[known.id]?.let { known.copy(applied = it.applied, confirmed = it.confirmed) } ?: known }
                }
                state.copy(known = state.known + loaded, progress = state.progress + shown.associate { it.id to it.toProgress(marks[it.id].orEmpty(), today) },
                    programActivities = state.programActivities + (programId to ProgramActivities(ProgramPhase.LOADED, open.map { it.id }, upcoming.map { it.id }, past.map { it.id })))
            }
        }
    }
    fun schedules(id: String) = models[id]?.schedules.orEmpty()
    /** The timed range of one stage for the calendar comparison; null for an undated, all-day or unknown stage. */
    fun stageWindow(id: String, stage: Int) = models[id]?.stages()?.getOrNull(stage)?.window
    /** The user marks a stage 통과/대기/탈락; the track is rebuilt from today's date. */
    fun mark(id: String, stage: Int, value: ProgressMark) {
        val model = models[id] ?: return
        val own = marks[id].orEmpty() + (stage to value)
        marks = marks + (id to own)
        mutable.update { it.copy(progress = it.progress + (id to model.toProgress(own, today()))) }
    }
    fun apply(id: String, value: Boolean) = update(id) { it.copy(applied = value, confirmed = value && it.confirmed) }
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
            ApplyAnswer.APPLIED -> apply(id, true)
            ApplyAnswer.NOT_YET -> Unit
            ApplyAnswer.NEVER_ASK -> neverAsk += id
        }
    }
    fun confirm(id: String, value: Boolean) = update(id) { it.copy(confirmed = it.applied && value) }
    private fun update(id: String, change: (ActivityState) -> ActivityState) {
        mutable.update { state -> state.known[id]?.let { state.copy(known = state.known + (id to change(it))) } ?: state }
    }
}

private const val MAX_PROGRAM_PAGES = 5
/** 지난 활동 window: the last three years (2026-10-19 저녁, 2년 → 3년). */
private const val PAST_DAYS = 1095L
private fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
private val sessionTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val sessionDay = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
private val periodTime = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))
private fun zoneOf(id: String) = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Seoul"))

private fun ActivityModel.toState(now: Instant) = ActivityState(
    id, name, description, organization, program, statusLabel(now), dateLabel, address, safeHttpsUrl(imageUrl),
    schedules.map { schedule ->
        val zone = zoneOf(schedule.timeZone)
        val range = listOf(schedule.startAt, schedule.endAt).joinToString(" – ") { sessionTime.withZone(zone).format(instant(it)) }
        // Several days: prefix the date so sessions are not read as one day (web rule).
        val days = schedules.map { sessionDay.withZone(zoneOf(it.timeZone)).format(instant(it.startAt)) }.toSet()
        (if (days.size > 1) sessionDay.withZone(zone).format(instant(schedule.startAt)) + " " + range else range) to schedule.title
    },
    safeHttpsUrl(officialUrl), applyUrl(now), period(), recruitmentEnd, schedules.firstOrNull()?.startAt,
    eligibility = eligibility?.lines() ?: emptyList(),
    category = category?.label, categoryOrder = category?.ordinal ?: Int.MAX_VALUE,
    sortKey = sortKey,
    tags = tags.map { it.name },
)
/**
 * The stage track with the user's [marks]: a 통과 mark makes the stage past, the earliest other stage not over by [today]
 * is current, a 탈락 mark or no current stage ends the activity. No stages at all leaves an empty track.
 */
private fun ActivityModel.toProgress(marks: Map<Int, ProgressMark>, today: LocalDate): ProgressState {
    val stages = stages()
    val passed = marks.filterValues { it == ProgressMark.PASS }.keys
    val failedAt = marks.filterValues { it == ProgressMark.FAIL }.keys.minOrNull()
    val current = if (failedAt == null) currentStage(stages, passed, today) else null
    val ended = stages.isNotEmpty() && (failedAt != null || current == null)
    val shown = stages.mapIndexed { index, stage ->
        val status = when {
            index == current -> ProgressStatus.CURRENT
            index in passed || stage.finished(today) || index <= (failedAt ?: (current ?: 0) - 1) -> ProgressStatus.PAST
            else -> ProgressStatus.UPCOMING
        }
        ProgressStage(stage.name, stage.kind, stage.dateText, status, marks[index] ?: ProgressMark.WAIT, stage.window != null)
    }
    val badge = when { stages.isEmpty() -> ""; failedAt != null -> "탈락"; current == null -> "지남"; else -> stageBadge(stages[current], today) }
    return ProgressState(id, name, "$program · $organization", shown, current, badge, ended, failedAt != null, current?.let { stages[it].day })
}

/** "10월 1일 14:07 – 10월 8일 00:00" in Korean time; an unknown end stays blank on its side. */
private fun ActivityModel.period(): String? {
    val start = instant(application?.startAt)
    val end = recruitmentEnd
    if (start == null && end == null) return null
    return listOf(start, end).joinToString(" – ") { it?.let(periodTime::format).orEmpty() }.trim()
}
