package com.dearby.nativeapp.pages.activity

import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import java.time.Instant
import java.time.LocalDate

enum class ActivityListPhase { LOADING, FAILED, LOADED }
enum class ProgramPhase { LOADING, FAILED, LOADED }

/** One activity as shown; recruiting and links are judged once at load time. */
data class ActivityState(
    val id: String, val title: String, val summary: String, val organization: String, val program: String, val status: String,
    val date: String, val location: String?, val imageUrl: String?, val sessions: List<Pair<String, String>>,
    val officialUrl: String?, val applyUrl: String?, val period: String?, val recruitmentEnd: Instant?, val startAt: String?,
    // Example application, plus the user's own (not the organizer's) participation mark.
    val applied: Boolean = false, val confirmed: Boolean = false,
    /** 대상 lines in Korean (contract "대상자 조건"); empty when the activity has no eligibility. */
    val eligibility: List<String> = emptyList(),
    /** 활동 종류 label (부트캠프 …) in display order via [categoryOrder]; null = 미분류, grouped last. */
    val category: String? = null, val categoryOrder: Int = Int.MAX_VALUE,
    /** Discovery order inside a category group: recruitment deadline, else activity start; null last. */
    val sortKey: Instant? = null,
)
/**
 * One program header in discovery (contract "발견 프로그램 묶음"); [matched] when the search hit an open activity's name.
 * [openCount] is 0 for a program shown only because it had an activity in the last two years; [latestActivityAt] is its most
 * recent activity start (contract "모집 중인 프로그램만 보기").
 */
data class ProgramState(val id: String, val title: String, val organization: String, val openCount: Int, val deadline: Instant?, val matched: Boolean, val latestActivityAt: Instant? = null)
/**
 * The open activities of an expanded program, loaded on first expand; [ids] index into [ActivityListState.known]. When none is
 * open, [upcomingIds] are the program's activities whose recruitment (or activity) start is still ahead, nearest first, and
 * [pastIds] those of the last two years, most recent start first (contract "모집 중인 프로그램만 보기").
 */
data class ProgramActivities(
    val phase: ProgramPhase = ProgramPhase.LOADING, val ids: List<String> = emptyList(), val upcomingIds: List<String> = emptyList(), val pastIds: List<String> = emptyList(),
)
/**
 * Discovery results for [query]: [programs] in server order, which are [expanded] (memory only), and their loaded
 * [programActivities]. [known] keeps every loaded activity with its marks so 내 활동 survives a new search.
 * [nextCursor] is null on the last program page; a failed next page sets [moreFailed] only.
 * [askingId]: the activity whose application link was opened, asked about once when the app comes back (contract #148).
 * [progress] is every loaded activity's stage track with the user's marks (contract "내 활동 진행 트랙"), memory only.
 * [openOnly] is the `모집 중만 보기` switch (default on, memory only; contract "모집 중인 프로그램만 보기").
 */
data class ActivityListState(
    val phase: ActivityListPhase = ActivityListPhase.LOADING, val query: String = "", val openOnly: Boolean = true, val programs: List<ProgramState> = emptyList(),
    val expanded: Set<String> = emptySet(), val programActivities: Map<String, ProgramActivities> = emptyMap(),
    val known: Map<String, ActivityState> = emptyMap(), val nextCursor: String? = null, val loadingMore: Boolean = false,
    val moreFailed: Boolean = false, val askingId: String? = null, val progress: Map<String, ProgressState> = emptyMap(),
) {
    val asking get() = askingId?.let(known::get)
    /** Applied activities, the nearest next stage first; activities without a dated next stage come last. */
    val appliedActivities get() = known.values.filter { it.applied }
        .sortedWith(compareBy({ progress[it.id]?.nextDay == null }, { progress[it.id]?.nextDay ?: LocalDate.MAX }, { it.title }))
    val activeActivities get() = appliedActivities.filter { progress[it.id]?.ended != true }
    val endedActivities get() = appliedActivities.filter { progress[it.id]?.ended == true }
}
