package com.dearby.nativeapp.pages.catalog

data class ActivityState(
    val id: String, val title: String, val summary: String, val status: String,
    val participation: String, val date: String, val fields: List<Pair<String, String>>,
    val source: String, val startAt: String,
    // Example application, plus the user's own (not the organizer's) participation mark.
    val applied: Boolean = false, val confirmed: Boolean = false,
    val sessions: List<Pair<String, String>> = emptyList(),
)
data class CatalogState(val activities: List<ActivityState>, val filter: String = "전체") {
    val visibleActivities get() = activities.filter { filter == "전체" || it.participation.startsWith(filter) }
    val appliedActivities get() = activities.filter { it.applied }.sortedBy { it.startAt }
}
