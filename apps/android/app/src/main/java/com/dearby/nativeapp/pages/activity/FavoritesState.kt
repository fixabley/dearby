package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.dearby.nativeapp.shared.ui.*
import java.time.LocalDate

/**
 * The hearts on activities and programs (contract "즐겨찾기(하트)·새 모집 푸시 알림"): which ids are favorited and their 알림 수준 ([levels] by
 * [favoriteKey]). [toggle] takes `program` and the id; signed out the caller opens sign-in instead. [setLevel] saves the bell's choice.
 */
data class FavoriteHearts(
    val activities: Set<String> = emptySet(), val programs: Set<String> = emptySet(), val levels: Map<String, BellLevel> = emptyMap(),
    val toggle: (Boolean, String) -> Unit = { _, _ -> }, val setLevel: (Boolean, String, BellLevel) -> Unit = { _, _, _ -> },
) {
    fun level(program: Boolean, id: String) = levels[favoriteKey(program, id)] ?: BellLevel.APPLICATION
}
fun favoriteKey(program: Boolean, id: String) = (if (program) "programs/" else "activities/") + id

enum class FavoritesPhase { SIGNED_OUT, LOADING, FAILED, LOADED }
/** A favorited program: its discovery card state and the `새 모집` badge. */
data class FavoriteProgramRow(val program: ProgramState, val newRecruitment: Boolean)
/** What a calendar date carries: the recruitment end (`마감`), start (`모집 시작`), or an event start for an activity without a window (`행사`). */
enum class CalendarMark(val label: String) { DEADLINE("마감"), START("모집 시작"), EVENT("행사") }
/** One dated line of the calendar list; [date] null is an activity without any date (`날짜 없는 즐겨찾기`). */
data class CalendarEntry(val date: LocalDate?, val activityId: String, val title: String, val program: String, val mark: CalendarMark?)

/**
 * 즐겨찾기 (내 활동 tab's second top tab): [programs] then [activityIds] (server order: nearest deadline first, past last; rows come from
 * [ActivityListState.known]), the calendar [entries] (sorted, `날짜 미정` last) and [today] for dimming past dates.
 */
data class FavoritesViewState(
    val phase: FavoritesPhase = FavoritesPhase.SIGNED_OUT, val programs: List<FavoriteProgramRow> = emptyList(), val activityIds: List<String> = emptyList(),
    val entries: List<CalendarEntry> = emptyList(), val today: LocalDate = LocalDate.now(),
)

/**
 * (Favorited) the bell, the heart, then for an activity ([added] non-null) the `+`, so both sit right beside the heart (contract "`+` 내 활동에 추가").
 * The bell opens the level sheet here.
 */
@Composable internal fun FavoriteActions(hearts: FavoriteHearts, program: Boolean, id: String, title: String, added: Boolean? = null, addToMine: () -> Unit = {}) {
    var sheet by remember { mutableStateOf(false) }
    val favorited = id in (if (program) hearts.programs else hearts.activities)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (favorited) BellButton(hearts.level(program, id), { sheet = true })
        FavoriteButton(favorited, { hearts.toggle(program, id) })
        if (added != null) AddToMineButton(added, addToMine)
    }
    if (sheet) NotifyLevelSheet("$title 알림", hearts.level(program, id), { hearts.setLevel(program, id, it); sheet = false }) { sheet = false }
}
