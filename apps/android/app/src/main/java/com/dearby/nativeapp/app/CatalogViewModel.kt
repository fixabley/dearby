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
    fun filter(value: String) { require(value in listOf("전체", "참가등록형", "선발형")); mutable.update { it.copy(filter = value) } }
    fun apply(id: String, value: Boolean) = update(id) { it.copy(applied = value, confirmed = value && it.confirmed) }
    fun confirm(id: String, value: Boolean) = update(id) { it.copy(confirmed = it.applied && value) }
    private fun update(id: String, change: (ActivityState) -> ActivityState) {
        mutable.update { state -> state.copy(activities = state.activities.map { if (it.id == id) change(it) else it }) }
    }
}

private val sessionTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val checkedTime = DateTimeFormatter.ofPattern("M월 d일 HH:mm (한국 시간)", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

private fun ActivityModel.toState(now: Instant) = ActivityState(
    id, title, summary, organization, statusLabel(now), if (participation == "selection") "선발형" else "참가등록형", isOpen(now),
    dateLabel, location, cost, audience, roles,
    schedules.map { schedule ->
        val zone = runCatching { ZoneId.of(schedule.timeZone) }.getOrDefault(ZoneId.of("Asia/Seoul"))
        val range = listOf(schedule.startAt, schedule.endAt).joinToString(" – ") { sessionTime.withZone(zone).format(instant(it)) }
        range to schedule.title
    },
    safeHttpsUrl(officialUrl), applyUrl(now), quickApplyUrl(now), recruitmentEnd, sourceNote,
    instant(sourceCheckedAt)?.let(checkedTime::format), schedules.firstOrNull()?.startAt,
)
