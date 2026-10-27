package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.stages
import com.dearby.nativeapp.features.analytics.Analytics
import com.dearby.nativeapp.pages.onboarding.TodayEventChoice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 오늘 행사 온보딩 (2026-10-27 사용자 "오늘 진행 중인 행사 중 참여 중인 게 있는지 물어보는 온보딩"). [check] reads the short events not open for
 * application ([fetch]: `GET /v1/activities?category=CONFERENCE,MEETUP,LECTURE,WORKSHOP&status=IN_PROGRESS,RECRUITMENT_CLOSED,NO_RECRUITMENT&size=50`)
 * and asks about the ones with a 활동 일정 block on today's Seoul date that are not in 내 활동 yet, once per Seoul date on this device
 * ([DevicePreferences.todayOnboardingDay], set when the sheet shows; a day without one asks nothing and is checked again on the next return).
 * Signed in or not. analytics `today_onboarding_shown(count)` and `today_onboarding_answer(result, count)`.
 *
 * Debug only, [DevicePreferences.todayOnboardingFake]: the nearest coming day with such an event stands in for today, so the sheet and what
 * `참여해요` opens can be checked on days without one (the events and their timetables are the real ones).
 */
class TodayOnboardingViewModel(private val fetch: suspend () -> List<ActivityModel>, private val device: DevicePreferences, private val api: String) : ViewModel() {
    /** The question on screen: [day] is the date asked about (today, or the stand-in day of the Debug switch). */
    data class Ask(val day: LocalDate, val events: List<TodayEventChoice>, val models: List<ActivityModel>)
    private val askFlow = MutableStateFlow<Ask?>(null)
    val asking = askFlow.asStateFlow()
    private var job: Job? = null

    /** The app came to the front with nothing else in the way; [mine] are the 내 활동 ids to leave out. */
    fun check(mine: Set<String>) {
        val today = LocalDate.now(seoul)
        if (askFlow.value != null || job?.isActive == true || device.todayOnboardingDay == today.toString()) return
        val fake = BuildConfig.DEBUG && device.todayOnboardingFake
        job = viewModelScope.launch {
            val items = try { fetch() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Today events load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch
            }
            val open = items.filter { it.id !in mine }
            val day = if (fake) open.flatMap { model -> model.blocks().filter { it.lastDay!! >= today }.map { maxOf(it.day!!, today) } }.minOrNull() ?: return@launch else today
            val found = todayEvents(open, day)
            if (found.isEmpty()) return@launch
            device.todayOnboardingDay = today.toString()
            askFlow.value = Ask(day, found.map { (model, time) -> model.choice(time) }, found.map { it.first })
            Analytics.event("today_onboarding_shown", "count" to found.size.toString())
        }
    }

    /** `참여해요` with [chosen] (non-empty) or `오늘은 아니에요`/a swipe (empty): the sheet goes; returns the chosen activities for 내 활동. */
    fun answer(chosen: List<String>): List<ActivityModel> {
        val ask = askFlow.value ?: return emptyList()
        askFlow.value = null
        Analytics.event("today_onboarding_answer", "result" to if (chosen.isEmpty()) "skip" else "joined", "count" to chosen.size.toString())
        return chosen.mapNotNull { id -> ask.models.firstOrNull { it.id == id } }
    }

    /** Debug switch turned on: today is asked again (the fake day stands in from the next check). */
    val fake get() = device.todayOnboardingFake
    fun setFake(on: Boolean) { device.todayOnboardingFake = on; if (on) device.todayOnboardingDay = null }

    private fun ActivityModel.choice(time: String) = TodayEventChoice(id, name, program, programId?.let { "${api.trimEnd('/')}/v1/programs/$it/logo" }, venue, time)
}

/** 활동 일정 blocks (not 선발), dated, a week long at most (the "오늘 내 행사 카드" rule). */
private fun ActivityModel.blocks() = stages().filter { !it.selection && it.day != null && it.lastDay != null && ChronoUnit.DAYS.between(it.day, it.lastDay) <= 7 }

/** The events with a block on [day], earliest start first, at most 5, each with that day's range (`10:00 ~ 17:30`, `종일` without times). */
internal fun todayEvents(items: List<ActivityModel>, day: LocalDate): List<Pair<ActivityModel, String>> {
    val from = day.atStartOfDay(seoul).toInstant()
    val to = day.plusDays(1).atStartOfDay(seoul).toInstant()
    return items.mapNotNull { model ->
        val blocks = model.blocks().filter { it.day!! <= day && day <= it.lastDay!! }.ifEmpty { return@mapNotNull null }
        val timed = blocks.filter { !it.allDay && it.eventStart != null && it.eventEnd != null && it.eventEnd > it.eventStart }
        val start = timed.minOfOrNull { maxOf(it.eventStart!!, from) }
        val end = timed.maxOfOrNull { minOf(it.eventEnd!!, to) }
        Triple(model, start, if (start == null || end == null) "종일" else "${clock(start)} ~ ${if (end == to) "24:00" else clock(end)}")
    }.sortedWith(compareBy(nullsLast()) { it.second }).take(5).map { it.first to it.third }
}

private fun clock(at: Instant) = time.format(at.atZone(seoul))
private val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
