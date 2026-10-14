package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.catalog.model.ActivityModel
import com.dearby.nativeapp.entities.catalog.model.ActivityPage
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.entities.catalog.model.instant
import com.dearby.nativeapp.entities.catalog.model.safeHttpsUrl
import com.dearby.nativeapp.pages.catalog.ActivityState
import com.dearby.nativeapp.pages.catalog.CatalogPhase
import com.dearby.nativeapp.pages.catalog.CatalogState
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
 * Discovery reads GET /v1/activities page by page; a failure is an error state, never example activities.
 * [fetch] takes the search text (null for all) and the cursor (null for the first page).
 */
class CatalogViewModel(private val fetch: suspend (String?, String?) -> ActivityPage) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState())
    val state = mutable.asStateFlow()
    private var schedules: Map<String, List<ScheduleModel>> = emptyMap()
    // One request at a time: a new search cancels the previous search or page, so late answers are dropped.
    private var job: Job? = null
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
        job = viewModelScope.launch {
            if (debounce) delay(300)
            mutable.update { it.copy(phase = CatalogPhase.LOADING, ids = emptyList(), nextCursor = null, loadingMore = false, moreFailed = false) }
            val page = page(null) ?: return@launch mutable.update { it.copy(phase = CatalogPhase.FAILED) }
            show(page, append = false)
        }
    }
    /** The list is near its end: fetch the next page, if there is one and nothing else is loading. */
    fun loadMore() {
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.phase != CatalogPhase.LOADED || current.loadingMore || job?.isActive == true) return
        job = viewModelScope.launch {
            mutable.update { it.copy(loadingMore = true, moreFailed = false) }
            val page = page(cursor) ?: return@launch mutable.update { it.copy(loadingMore = false, moreFailed = true) }
            show(page, append = true)
        }
    }
    private suspend fun page(cursor: String?): ActivityPage? = try {
        fetch(state.value.query.trim().ifEmpty { null }, cursor)
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        // Only the origin and the failure kind; the search text is not logged.
        Log.w("Dearby", "Activities load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
        null
    }
    private fun show(page: ActivityPage, append: Boolean) {
        val now = Instant.now()
        schedules = schedules + page.items.associate { it.id to it.schedules }
        mutable.update { state ->
            val loaded = page.items.associate { activity ->
                activity.id to activity.toState(now).let { shown -> state.known[shown.id]?.let { shown.copy(applied = it.applied, confirmed = it.confirmed) } ?: shown }
            }
            val ids = (if (append) state.ids else emptyList()) + page.items.map { it.id }
            state.copy(phase = CatalogPhase.LOADED, ids = ids, known = state.known + loaded, nextCursor = page.nextCursor, loadingMore = false)
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
)
/** "10월 1일 14:07 – 10월 8일 00:00" in Korean time; an unknown end stays blank on its side. */
private fun ActivityModel.period(): String? {
    val start = instant(application?.startAt)
    val end = recruitmentEnd
    if (start == null && end == null) return null
    return listOf(start, end).joinToString(" – ") { it?.let(periodTime::format).orEmpty() }.trim()
}
