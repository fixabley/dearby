package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.activity.api.parseFavorites
import com.dearby.nativeapp.entities.activity.model.ActivityModel
import com.dearby.nativeapp.entities.activity.model.FavoriteActivity
import com.dearby.nativeapp.entities.activity.model.FavoriteProgram
import com.dearby.nativeapp.entities.activity.model.NotifyLevel
import com.dearby.nativeapp.entities.activity.model.instant
import com.dearby.nativeapp.entities.activity.model.stages
import com.dearby.nativeapp.pages.activity.CalendarEntry
import com.dearby.nativeapp.pages.activity.CalendarMark
import com.dearby.nativeapp.pages.activity.FavoriteProgramRow
import com.dearby.nativeapp.pages.activity.FavoritesPhase
import com.dearby.nativeapp.pages.activity.FavoritesViewState
import com.dearby.nativeapp.pages.activity.favoriteKey
import com.dearby.nativeapp.shared.ui.BellLevel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25), signed in only. The favorited ids come from `GET /v1/me/favorites` (the
 * discovery reads are anonymous, so they never carry `favorited`); a heart changes them at once and `PUT`/`DELETE` follows, and a failure (a 404
 * before the server ships it included) puts the heart back and sets [notice]. The 맞춤 대기열 ♥ is the same activity heart: the server records its
 * LIKE/UNLIKE itself. [adopt] hands the activities to discovery so their rows and details open.
 */
class FavoritesViewModel(
    private val account: AccountViewModel, private val adopt: (List<ActivityModel>) -> Unit,
) : ViewModel() {
    data class Ids(val activities: Set<String> = emptySet(), val programs: Set<String> = emptySet(), val levels: Map<String, BellLevel> = emptyMap())
    private val mutableIds = MutableStateFlow(Ids())
    val ids = mutableIds.asStateFlow()
    private val mutable = MutableStateFlow(FavoritesViewState())
    val state = mutable.asStateFlow()
    private val noticeFlow = MutableStateFlow<String?>(null)
    val notice = noticeFlow.asStateFlow()
    private var programs: List<FavoriteProgram> = emptyList()
    private var activities: List<FavoriteActivity> = emptyList()
    private var loadJob: Job? = null
    // Writes go in order, so a quick heart → un-heart reaches the server as PUT then DELETE.
    private val writes = Mutex()
    private val signedIn get() = account.ready.value

    init {
        viewModelScope.launch {
            account.ready.map { if (it) AccountPhase.SIGNED_IN else AccountPhase.SIGNED_OUT }.collect { phase ->
                if (phase == AccountPhase.SIGNED_IN) load() else clear()
            }
        }
    }

    private fun clear() {
        loadJob?.cancel()
        programs = emptyList(); activities = emptyList()
        mutableIds.value = Ids()
        mutable.value = FavoritesViewState(FavoritesPhase.SIGNED_OUT)
    }

    /** Reads the account's favorites; a 404 (not deployed yet) is an empty list, other failures the retry state. */
    fun load() {
        if (!signedIn) return
        loadJob?.cancel()
        if (mutable.value.phase != FavoritesPhase.LOADED) mutable.update { it.copy(phase = FavoritesPhase.LOADING) }
        loadJob = viewModelScope.launch {
            try {
                val got = parseFavorites(account.authorized { account.client.favorites(it) }, ApiOrigin.current)
                programs = got.programs; activities = got.activities
                mutableIds.value = Ids(activities.map { it.activity.id }.toSet(), programs.map { it.program.id }.toSet(),
                    programs.associate { favoriteKey(true, it.program.id) to it.notifyLevel.bell() } + activities.associate { favoriteKey(false, it.activity.id) to it.notifyLevel.bell() })
                adopt(activities.map { it.activity })
                show()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                Log.w("Dearby", "Favorites load from ${ApiOrigin.current} failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
                if ((e as? AccountException)?.error == AccountError.NOT_FOUND) { programs = emptyList(); activities = emptyList(); show() }
                else mutable.update { it.copy(phase = FavoritesPhase.FAILED) }
            }
        }
    }

    private fun show() = mutable.update { it.copy(phase = FavoritesPhase.LOADED, programs = programs.map { p -> FavoriteProgramRow(p.program.toState(), p.newRecruitment) },
        activityIds = activities.map { a -> a.activity.id }, entries = calendar(activities), today = today()) }

    /** A heart tap: on → `PUT` (the server's default level, `신청일만`) and the list is read again; off → `DELETE`. */
    fun toggle(program: Boolean, id: String) {
        if (!signedIn) return
        val before = Triple(programs, activities, mutableIds.value)
        val on = id !in (if (program) before.third.programs else before.third.activities)
        mutableIds.update { ids ->
            val key = favoriteKey(program, id)
            if (program) ids.copy(programs = if (on) ids.programs + id else ids.programs - id, levels = if (on) ids.levels + (key to BellLevel.APPLICATION) else ids.levels - key)
            else ids.copy(activities = if (on) ids.activities + id else ids.activities - id, levels = if (on) ids.levels + (key to BellLevel.APPLICATION) else ids.levels - key)
        }
        if (!on) { if (program) programs = programs.filterNot { it.program.id == id } else activities = activities.filterNot { it.activity.id == id } }
        if (mutable.value.phase == FavoritesPhase.LOADED) show()
        write(revert = { programs = before.first; activities = before.second; mutableIds.value = before.third; show() }, refresh = on) {
            if (on) account.client.saveFavorite(kind(program), id, null, it) else account.client.deleteFavorite(kind(program), id, it)
        }
    }

    /** The bell's choice for an existing favorite (`PUT … {notifyLevel}`). */
    fun setLevel(program: Boolean, id: String, level: BellLevel) {
        val before = mutableIds.value
        mutableIds.update { it.copy(levels = it.levels + (favoriteKey(program, id) to level)) }
        write(revert = { mutableIds.value = before }) { account.client.saveFavorite(kind(program), id, level.name, it) }
    }

    /** A program opened from 즐겨찾기: its `새 모집` badge goes (`…/seen`); a failure is only logged. */
    fun seen(programId: String) {
        val row = programs.firstOrNull { it.program.id == programId }?.takeIf { it.newRecruitment } ?: return
        programs = programs.map { if (it === row) it.copy(newRecruitment = false) else it }
        show()
        viewModelScope.launch {
            try { account.authorized { account.client.seenFavoriteProgram(programId, it) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Dearby", "Favorite seen failed: ${e.javaClass.simpleName}") }
        }
    }

    fun dismissNotice() { noticeFlow.value = null }

    private fun write(revert: () -> Unit, refresh: Boolean = false, call: suspend (AccountSession) -> Unit) {
        viewModelScope.launch {
            try {
                writes.withLock { account.authorized(call) }
                if (refresh) load()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                Log.w("Dearby", "Favorite write to ${ApiOrigin.current} failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
                revert()
                noticeFlow.value = "즐겨찾기를 바꾸지 못했어요. 잠시 뒤 다시 시도해 주세요."
            }
        }
    }

    private fun kind(program: Boolean) = if (program) "programs" else "activities"
}

private fun NotifyLevel.bell() = when (this) { NotifyLevel.ALL -> BellLevel.ALL; NotifyLevel.APPLICATION -> BellLevel.APPLICATION; NotifyLevel.NONE -> BellLevel.NONE }
private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
private fun today(): LocalDate = LocalDate.now(SEOUL)

/**
 * The calendar's lines (contract "즐겨찾기 활동 캘린더 보기"): the recruitment window's start (`모집 시작`) and end (`마감`, the same Seoul day the
 * 내 활동 track counts to); an activity without a window shows its event start (`행사`); one with no date at all is `날짜 미정`. Sorted by date.
 */
private fun calendar(activities: List<FavoriteActivity>): List<CalendarEntry> = activities.flatMap { favorite ->
    val activity = favorite.activity
    val program = favorite.program.name
    fun entry(day: LocalDate?, mark: CalendarMark?) = CalendarEntry(day, activity.id, activity.name, program, mark)
    val window = activity.application
    val dated = if (window != null) listOfNotNull(
        (instant(window.startAt)?.atZone(SEOUL)?.toLocalDate() ?: window.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() })?.let { entry(it, CalendarMark.START) },
        activity.stages().firstOrNull { it.kind == "APPLICATION" }?.day?.let { entry(it, CalendarMark.DEADLINE) },
    ) else listOfNotNull(activity.activityStart?.atZone(SEOUL)?.toLocalDate()?.let { entry(it, CalendarMark.EVENT) })
    dated.ifEmpty { listOf(entry(null, null)) }
}.sortedWith(compareBy(nullsLast()) { it.date })
