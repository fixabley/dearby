package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.lines
import com.dearby.nativeapp.entities.activity.model.ActivityPage
import com.dearby.nativeapp.entities.activity.model.ProgramPage
import com.dearby.nativeapp.entities.activity.model.ScheduleModel
import com.dearby.nativeapp.entities.activity.model.instant
import com.dearby.nativeapp.entities.activity.model.safeHttpsUrl
import com.dearby.nativeapp.pages.activity.ActivityState
import com.dearby.nativeapp.pages.activity.ActivityListPhase
import com.dearby.nativeapp.pages.activity.ActivityListState
import com.dearby.nativeapp.pages.activity.ProgramActivities
import com.dearby.nativeapp.pages.activity.ProgramPhase
import com.dearby.nativeapp.pages.activity.ProgramState
import java.time.Instant
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
 * (null for all) and the cursor; [fetch] takes the search text, the cursor and the program id.
 */
class ActivityViewModel(
    private val fetchPrograms: suspend (String?, String?) -> ProgramPage,
    private val fetch: suspend (String?, String?, String?) -> ActivityPage,
) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityListState())
    val state = mutable.asStateFlow()
    private var schedules: Map<String, List<ScheduleModel>> = emptyMap()
    // One program request at a time: a new search cancels the previous search, page and program loads, so late answers are dropped.
    private var job: Job? = null
    private val programJobs = mutableMapOf<String, Job>()
    private var opened: String? = null
    private var leftApp = false
    private val neverAsk = mutableSetOf<String>()

    init { reload() }

    /** Typing: show the text now, ask the server once it has rested for 300 ms. */
    fun search(text: String) {
        if (text == state.value.query) return
        mutable.update { it.copy(query = text) }
        start(debounce = true)
    }
    fun reload() = start(debounce = false)
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
        fetchPrograms(state.value.query.trim().ifEmpty { null }, cursor)
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        // Only the origin and the failure kind; the search text is not logged.
        Log.w("Dearby", "Programs load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
        null
    }
    /** Programs with one open activity, or whose activity name matched the search, start expanded (contract rule). */
    private fun show(page: ProgramPage, append: Boolean) {
        val programs = page.items.map { ProgramState(it.id, it.name, it.organization, it.openActivityCount, instant(it.nearestRecruitmentEndAt), it.matchedActivityIds.isNotEmpty()) }
        val opening = programs.filter { it.openCount == 1 || it.matched }.map { it.id }
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
                // Every page of the program (the open set is small); the client keeps only open activities, as the list did before.
                repeat(MAX_PROGRAM_PAGES) {
                    val page = fetch(null, cursor, programId)
                    items += page.items
                    cursor = page.nextCursor ?: return@repeat
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Program activities load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch mutable.update { it.copy(programActivities = it.programActivities + (programId to ProgramActivities(ProgramPhase.FAILED))) }
            }
            val now = Instant.now()
            val open = items.filter { it.isOpen(now) }
            schedules = schedules + open.associate { it.id to it.schedules }
            mutable.update { state ->
                val loaded = open.associate { activity ->
                    activity.id to activity.toState(now).let { shown -> state.known[shown.id]?.let { shown.copy(applied = it.applied, confirmed = it.confirmed) } ?: shown }
                }
                state.copy(known = state.known + loaded, programActivities = state.programActivities + (programId to ProgramActivities(ProgramPhase.LOADED, open.map { it.id })))
            }
        }
    }
    fun schedules(id: String) = schedules[id].orEmpty()
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
)
/** "10월 1일 14:07 – 10월 8일 00:00" in Korean time; an unknown end stays blank on its side. */
private fun ActivityModel.period(): String? {
    val start = instant(application?.startAt)
    val end = recruitmentEnd
    if (start == null && end == null) return null
    return listOf(start, end).joinToString(" – ") { it?.let(periodTime::format).orEmpty() }.trim()
}
