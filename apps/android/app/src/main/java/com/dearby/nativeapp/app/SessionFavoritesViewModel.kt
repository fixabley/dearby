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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 트랙·세션 시간표 and 세션 찜 (contract "트랙·세션 시간표", 2026-10-26). The open timetable is read without a token; which sessions are
 * 찜 comes from `GET /v1/me/session-favorites` once signed in with consent ([AccountViewModel.ready]), the same list 내 시간표 shows. A heart
 * changes [ids] at once and `PUT`/`DELETE` follows in order; a failure puts it back and sets [notice]. Session 찜 has no side effects on
 * activity hearts, alerts or recommendations.
 */
class SessionFavoritesViewModel(private val account: AccountViewModel) : ViewModel() {
    data class Open(val activityId: String, val scheduleId: String, val phase: TimetablePhase = TimetablePhase.LOADING, val timetable: TimetableState? = null)

    private val openFlow = MutableStateFlow<Open?>(null)
    val open = openFlow.asStateFlow()
    private val idsFlow = MutableStateFlow<Set<String>>(emptySet())
    val ids = idsFlow.asStateFlow()
    private val itemsFlow = MutableStateFlow<List<SessionFavorite>>(emptyList())
    /** 내 시간표 (session start order). */
    val items = itemsFlow.asStateFlow()
    // The tracks of each block a 찜한 세션 is in, by schedule id: `/me/session-favorites` sends only `trackId`, so the block's timetable is read
    // once (anonymously) for the track name and room 나만의 시간표 shows. A failed read leaves the card without its track.
    private val tracksFlow = MutableStateFlow<Map<String, List<TrackState>>>(emptyMap())
    /** 나만의 시간표 across activities (즐겨찾기 › 찜한 세션), session start order. */
    val entries = combine(itemsFlow, tracksFlow) { items, tracks ->
        items.map { item ->
            val list = tracks[item.scheduleId].orEmpty()
            val track = list.firstOrNull { it.id == item.session.trackId }
            MyTimetableEntry(item.activityId, item.activityName, item.session.toState(), track, list.indexOf(track))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val noticeFlow = MutableStateFlow<String?>(null)
    val notice = noticeFlow.asStateFlow()
    private var timetableJob: Job? = null
    private var loadJob: Job? = null
    private val writes = Mutex()

    init {
        viewModelScope.launch {
            account.ready.collect { ready -> if (ready) load() else { loadJob?.cancel(); idsFlow.value = emptySet(); itemsFlow.value = emptyList() } }
        }
    }

    /** Shows the block's timetable; null closes it. */
    fun show(activityId: String?, scheduleId: String?) {
        timetableJob?.cancel()
        if (activityId == null || scheduleId == null) { openFlow.value = null; return }
        openFlow.value = Open(activityId, scheduleId)
        timetableJob = viewModelScope.launch {
            val result = try { withContext(Dispatchers.IO) { fetchTimetable(ApiOrigin.current, activityId, scheduleId) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w("Dearby", "Timetable load from ${ApiOrigin.current} failed: ${e.javaClass.simpleName}")
                return@launch openFlow.update { it?.copy(phase = TimetablePhase.FAILED) }
            }
            openFlow.update { it?.copy(phase = if (result == null) TimetablePhase.MISSING else TimetablePhase.LOADED, timetable = result?.toState()) }
        }
        load()
    }
    fun retry() = openFlow.value?.let { show(it.activityId, it.scheduleId) }

    /** Reads 내 시간표; a 404 (server without it) is an empty list, other failures keep what was shown. */
    fun load() {
        if (!account.ready.value) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val got = parseSessionFavorites(account.authorized { account.client.sessionFavorites(it) })
                itemsFlow.value = got; idsFlow.value = got.map { it.session.id }.toSet()
                readTracks(got)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.NOT_FOUND) { itemsFlow.value = emptyList(); idsFlow.value = emptySet() }
                else Log.w("Dearby", "Session favorites load from ${ApiOrigin.current} failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun readTracks(items: List<SessionFavorite>) {
        val missing = items.filter { it.scheduleId !in tracksFlow.value }.distinctBy { it.scheduleId }
        val read = missing.map { item ->
            viewModelScope.async(Dispatchers.IO) {
                runCatching { fetchTimetable(ApiOrigin.current, item.activityId, item.scheduleId) }.getOrNull()?.let { item.scheduleId to it.tracks.map { t -> TrackState(t.id, t.name, t.room) } }
            }
        }.awaitAll().filterNotNull()
        if (read.isNotEmpty()) tracksFlow.update { it + read }
    }

    /** A heart tap while [AccountViewModel.ready] (the caller opens sign-in or the consent screen otherwise). */
    fun toggle(sessionId: String) {
        if (!account.ready.value) return
        val before = idsFlow.value
        val on = sessionId !in before
        idsFlow.value = if (on) before + sessionId else before - sessionId
        if (!on) itemsFlow.update { list -> list.filterNot { it.session.id == sessionId } }
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
