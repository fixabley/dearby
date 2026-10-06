package com.dearby.nativeapp.pages.catalog

import java.time.Instant

enum class CatalogPhase { LOADING, FAILED, LOADED }

/** One catalog activity as shown; recruiting and links are judged once at load time. */
data class ActivityState(
    val id: String, val title: String, val summary: String, val organization: String?, val status: String,
    val participation: String, val open: Boolean, val date: String, val location: String?, val cost: String?,
    val audience: String?, val roles: List<String>, val sessions: List<Pair<String, String>>,
    val officialUrl: String?, val applyUrl: String?, val quickApplyUrl: String?, val recruitmentEnd: Instant?,
    val sourceNote: String, val checkedAt: String?, val startAt: String?,
    // Example application, plus the user's own (not the organizer's) participation mark.
    val applied: Boolean = false, val confirmed: Boolean = false,
)
data class CatalogState(val phase: CatalogPhase = CatalogPhase.LOADING, val activities: List<ActivityState> = emptyList(), val filter: String = "전체") {
    val visibleActivities get() = activities.filter { it.open && (filter == "전체" || it.participation == filter) }
    val appliedActivities get() = activities.filter { it.applied }.sortedBy { it.startAt ?: "~" }
}
