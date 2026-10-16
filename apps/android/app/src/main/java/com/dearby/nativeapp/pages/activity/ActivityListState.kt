package com.dearby.nativeapp.pages.activity

import java.time.Instant

enum class ActivityListPhase { LOADING, FAILED, LOADED }

/** One activity as shown; recruiting and links are judged once at load time. */
data class ActivityState(
    val id: String, val title: String, val summary: String, val organization: String, val program: String, val status: String,
    val date: String, val location: String?, val imageUrl: String?, val sessions: List<Pair<String, String>>,
    val officialUrl: String?, val applyUrl: String?, val period: String?, val recruitmentEnd: Instant?, val startAt: String?,
    // Example application, plus the user's own (not the organizer's) participation mark.
    val applied: Boolean = false, val confirmed: Boolean = false,
)
/**
 * Discovery results for [query] in server order ([ids]); [known] keeps every loaded activity with its marks so
 * 내 활동 survives a new search. [nextCursor] is null on the last page; a failed next page sets [moreFailed] only.
 * [askingId]: the activity whose application link was opened, asked about once when the app comes back (contract #148).
 */
data class ActivityListState(
    val phase: ActivityListPhase = ActivityListPhase.LOADING, val query: String = "", val ids: List<String> = emptyList(),
    val known: Map<String, ActivityState> = emptyMap(), val nextCursor: String? = null, val loadingMore: Boolean = false,
    val moreFailed: Boolean = false, val askingId: String? = null,
) {
    val asking get() = askingId?.let(known::get)
    val activities get() = ids.mapNotNull(known::get)
    val appliedActivities get() = known.values.filter { it.applied }.sortedBy { it.startAt ?: "~" }
}
