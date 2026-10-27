package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.activity.api.fetchTimetable
import com.dearby.nativeapp.entities.activity.api.parseSessionFavorites
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import com.dearby.nativeapp.entities.activity.model.Session
import com.dearby.nativeapp.entities.activity.model.Timetable
import com.dearby.nativeapp.pages.activity.DayBlock
import com.dearby.nativeapp.pages.activity.DayBlockLoad
import com.dearby.nativeapp.pages.activity.MyTimetableEntry
import com.dearby.nativeapp.pages.activity.SessionState
import com.dearby.nativeapp.pages.activity.TimetableState
import com.dearby.nativeapp.pages.activity.TrackState
import com.dearby.nativeapp.shared.ui.TagLabel
import com.dearby.nativeapp.pages.activity.TimetablePhase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 트랙·세션 시간표 and 세션 찜 (contract "트랙·세션 시간표", 2026-10-26). The open timetable is read without a token; which sessions are
 * 찜 comes from `GET /v1/me/session-favorites` once signed in with consent ([AccountViewModel.ready]), the same list 내 시간표 shows. A heart
 * changes [ids] at once and `PUT`/`DELETE` follows in order; a failure puts it back and sets [notice]. Session 찜 has no side effects on
 * activity hearts, alerts or recommendations. [items] feeds the device-local 세션 이동 알림 (features/reminder, planned by MainActivity), so it is
 * null until read: an unread list must not cancel the alarms already set.
 */
class SessionFavoritesViewModel(private val account: AccountViewModel) : ViewModel() {
    /**
     * [favoritesOnly] opens the sheet on 내 시간표 (a 세션 이동 알림 tap, 오늘 행사 온보딩). [highlightGuide] (오늘 행사 온보딩, 2026-10-27) asks the
     * sheet to stress its `행사 안내 시작` button (PR #464); the sheet ignores it until that button exists.
     */
    data class Open(val activityId: String, val scheduleId: String, val phase: TimetablePhase = TimetablePhase.LOADING, val timetable: TimetableState? = null, val favoritesOnly: Boolean = false,
        val highlightGuide: Boolean = false)

    /** 하루 시간표 (2026-10-26): one date's timed blocks, each read on its own; [loads] in block order. Open instead of [Open], never with it. */
    data class OpenDay(val activityId: String, val day: LocalDate, val loads: List<DayBlockLoad>, val favoritesOnly: Boolean = false, val highlightGuide: Boolean = false)

    private val openFlow = MutableStateFlow<Open?>(null)
    val open = openFlow.asStateFlow()
    private val dayFlow = MutableStateFlow<OpenDay?>(null)
    val openDay = dayFlow.asStateFlow()
    private val idsFlow = MutableStateFlow<Set<String>>(emptySet())
    val ids = idsFlow.asStateFlow()
    private val itemsFlow = MutableStateFlow<List<SessionFavorite>?>(null)
    /** 내 시간표 (session start order); null until read (and again once signed out or the consent is missing). */
    val items = itemsFlow.asStateFlow()
    /** 나만의 시간표 across activities (즐겨찾기 › 내 시간표), session start order; the track comes with each item (2026-10-26). */
    val entries = itemsFlow.map { items ->
        items.orEmpty().map { item ->
            val track = item.track?.let { TrackState(it.id, it.name, it.room) }
            MyTimetableEntry(item.activityId, item.activityName, item.session.toState(), track, item.track?.position ?: -1)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val noticeFlow = MutableStateFlow<String?>(null)
    val notice = noticeFlow.asStateFlow()
    /** A 세션 이동 알림 tap (MainActivity): the block whose timetable should open on 내 시간표 once its activity's detail is up ([showPending]). */
    private val pendingFlow = MutableStateFlow<Pair<String, String>?>(null)
    val pending = pendingFlow.asStateFlow()
    private var timetableJob: Job? = null
    private var loadJob: Job? = null
    private val writes = Mutex()

    init {
        viewModelScope.launch {
            account.ready.collect { ready -> if (ready) load() else { loadJob?.cancel(); idsFlow.value = emptySet(); itemsFlow.value = null } }
        }
    }

    /** Shows the block's timetable; null closes it (and a day sheet). */
    fun show(activityId: String?, scheduleId: String?, favoritesOnly: Boolean = false, highlightGuide: Boolean = false) {
        timetableJob?.cancel()
        dayFlow.value = null
        if (activityId == null || scheduleId == null) { openFlow.value = null; return }
        openFlow.value = Open(activityId, scheduleId, favoritesOnly = favoritesOnly, highlightGuide = highlightGuide)
        timetableJob = viewModelScope.launch {
            val result = try { withContext(Dispatchers.IO) { fetchTimetable(ApiOrigin.current, activityId, scheduleId) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Timetable load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch openFlow.update { it?.copy(phase = TimetablePhase.FAILED) }
            }
            // A 세션 이동 알림 tap whose block lost its timetable leaves only the detail open (contract "찜한 세션 이동 알림").
            if (result == null && favoritesOnly) return@launch run { openFlow.value = null }
            openFlow.update { it?.copy(phase = if (result == null) TimetablePhase.MISSING else TimetablePhase.LOADED, timetable = result?.toState()) }
        }
        load()
    }
    fun retry() {
        openFlow.value?.let { show(it.activityId, it.scheduleId, it.favoritesOnly, it.highlightGuide) }
        dayFlow.value?.let { showDay(it.activityId, it.day, it.loads.map { load -> load.block }, it.favoritesOnly, it.highlightGuide) }
    }

    /**
     * Shows the day's blocks as one table (하루 시간표): every block with a timetable is read at once, each landing in [OpenDay.loads] as it ends
     * (a 404 is MISSING, drawn as a title row; a failure FAILED). A block without a timetable is MISSING from the start and not fetched.
     */
    fun showDay(activityId: String, day: LocalDate, blocks: List<DayBlock>, favoritesOnly: Boolean = false, highlightGuide: Boolean = false) {
        timetableJob?.cancel()
        openFlow.value = null
        dayFlow.value = OpenDay(activityId, day, blocks.map { DayBlockLoad(it, if (it.hasTimetable && it.scheduleId != null) TimetablePhase.LOADING else TimetablePhase.MISSING, null) }, favoritesOnly, highlightGuide)
        timetableJob = viewModelScope.launch {
            blocks.filter { it.hasTimetable && it.scheduleId != null }.forEach { block ->
                launch {
                    val result = runCatching { withContext(Dispatchers.IO) { fetchTimetable(ApiOrigin.current, activityId, block.scheduleId!!) } }
                        .onFailure { if (it is CancellationException) throw it; Log.w("Dearby", "Timetable load from ${ApiOrigin.current} failed: ${it.javaClass.simpleName}") }
                    val (phase, timetable) = result.fold({ (if (it == null) TimetablePhase.MISSING else TimetablePhase.LOADED) to it?.toState() }, { TimetablePhase.FAILED to null })
                    dayFlow.update { open -> open?.copy(loads = open.loads.map { if (it.block.scheduleId == block.scheduleId) it.copy(phase = phase, timetable = timetable) else it }) }
                }
            }
        }
        load()
    }

    /** A 세션 이동 알림 was tapped: remember the block until the activity's detail opens (the caller opens that). */
    fun remind(activityId: String, scheduleId: String) { pendingFlow.value = activityId to scheduleId }
    /** The activity could not be opened (gone or unreadable): nothing waits any more. */
    fun dismissPending() { pendingFlow.value = null }
    /** The detail of [activityId] is up (ActivityRoute): if it is the remembered one, its timetable opens on 내 시간표. */
    fun showPending(activityId: String) {
        val (activity, schedule) = pendingFlow.value?.takeIf { it.first == activityId } ?: return
        pendingFlow.value = null
        show(activity, schedule, favoritesOnly = true)
    }

    /** Reads 내 시간표; a 404 (server without it) is an empty list, other failures keep what was shown. */
    fun load() {
        if (!account.ready.value) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val got = parseSessionFavorites(account.authorized { account.client.sessionFavorites(it) })
                itemsFlow.value = got; idsFlow.value = got.map { it.session.id }.toSet()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.NOT_FOUND) { itemsFlow.value = emptyList(); idsFlow.value = emptySet() }
                else Log.w("Dearby", "Session favorites load from ${ApiOrigin.current} failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
            }
        }
    }

    /** A heart tap while [AccountViewModel.ready] (the caller opens sign-in or the consent screen otherwise). */
    fun toggle(sessionId: String) {
        if (!account.ready.value) return
        val before = idsFlow.value
        val on = sessionId !in before
        idsFlow.value = if (on) before + sessionId else before - sessionId
        if (!on) itemsFlow.update { list -> list?.filterNot { it.session.id == sessionId } }
        viewModelScope.launch {
            try {
                writes.withLock { account.authorized { if (on) account.client.saveSessionFavorite(sessionId, it) else account.client.deleteSessionFavorite(sessionId, it) } }
                if (on) load()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                Log.w("Dearby", "Session favorite write to ${ApiOrigin.current} failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
                idsFlow.value = before
                load()
                noticeFlow.value = "찜을 바꾸지 못했어요. 잠시 뒤 다시 시도해 주세요."
            }
        }
    }
    fun dismissNotice() { noticeFlow.value = null }
}

private fun Timetable.toState() = TimetableState(scheduleTitle, tracks.map { TrackState(it.id, it.name, it.room) }, sessions.map { it.toState() })
internal fun Session.toState() = SessionState(id, trackId, kind, title, description, startAt, endAt,
    speakers.map { listOfNotNull(it.name, it.affiliation).joinToString(" · ") }, tags.map { TagLabel(it.name, it.nameEn) })
