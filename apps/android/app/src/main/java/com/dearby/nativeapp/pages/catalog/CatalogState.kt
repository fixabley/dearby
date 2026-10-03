package com.dearby.nativeapp.pages.catalog

data class ActivityState(
    val id: String, val title: String, val summary: String, val status: String,
    val participation: String, val date: String, val fields: List<Pair<String, String>>,
    val source: String, val report: Boolean = false,
    val saved: Boolean = false, val organizationSaved: Boolean = false,
    val sessions: List<Pair<String, String>> = emptyList(),
)
data class CatalogState(val activities: List<ActivityState>, val filter: String = "전체") {
    val visibleActivities get() = activities.filter { filter == "전체" || it.participation.startsWith(filter) }
}
