package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.entities.catalog.model.ActivityModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Discovery reads GET /v1/catalog; a failure is an error state, never example activities. */
class CatalogViewModel(private val fetch: suspend () -> List<ActivityModel>) : ViewModel() {
    private val mutable = MutableStateFlow(CatalogState())
    val state = mutable.asStateFlow()
    private var schedules: Map<String, List<ScheduleModel>> = emptyMap()
    private var opened: String? = null
    private var leftApp = false
    private val neverAsk = mutableSetOf<String>()

    suspend fun load() {
        mutable.update { it.copy(phase = CatalogPhase.LOADING) }
        val activities = try { fetch() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            mutable.update { it.copy(phase = CatalogPhase.FAILED) }
            return
        }
        val now = Instant.now()
        schedules = activities.associate { it.id to it.schedules }
        mutable.update { state ->
            val marks = state.activities.associateBy { it.id }
            state.copy(phase = CatalogPhase.LOADED, activities = activities.map { activity ->
                activity.toState(now).let { shown -> marks[shown.id]?.let { shown.copy(applied = it.applied, confirmed = it.confirmed) } ?: shown }
            })
        }
    }
    fun schedules(id: String) = schedules[id].orEmpty()
    fun filter(value: String) { require(value in listOf("전체", "바로 신청", "선발형")); mutable.update { it.copy(filter = value) } }
    fun apply(id: String, value: Boolean) = update(id) { it.copy(applied = value, confirmed = value && it.confirmed) }
    /** An application link for [id] was handed to the browser. Applied or "never ask" activities are not asked. */
    fun openedApplication(id: String) {
        if (state.value.activities.any { it.id == id && it.applied } || id in neverAsk) return
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
        mutable.update { state -> state.copy(activities = state.activities.map { if (it.id == id) change(it) else it }) }
    }
}

private val sessionTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val sessionDay = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
private fun zoneOf(id: String) = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Seoul"))
private val checkedTime = DateTimeFormatter.ofPattern("M월 d일 HH:mm (한국 시간)", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

private fun ActivityModel.toState(now: Instant) = ActivityState(
    id, title, summary, organization, statusLabel(now), if (participation == "selection") "선발형" else "바로 신청", isOpen(now),
    dateLabel, location, cost, audience, roles,
    schedules.map { schedule ->
        val zone = zoneOf(schedule.timeZone)
        val range = listOf(schedule.startAt, schedule.endAt).joinToString(" – ") { sessionTime.withZone(zone).format(instant(it)) }
        // Several days: prefix the date so sessions are not read as one day (web rule).
        val days = schedules.map { sessionDay.withZone(zoneOf(it.timeZone)).format(instant(it.startAt)) }.toSet()
        (if (days.size > 1) sessionDay.withZone(zone).format(instant(schedule.startAt)) + " " + range else range) to schedule.title
    },
    safeHttpsUrl(officialUrl), applyUrl(now), quickApplyUrl(now), recruitmentEnd, sourceNote,
    instant(sourceCheckedAt)?.let(checkedTime::format), schedules.firstOrNull()?.startAt,
)
