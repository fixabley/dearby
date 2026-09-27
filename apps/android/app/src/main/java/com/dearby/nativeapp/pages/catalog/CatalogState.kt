package com.dearby.nativeapp.pages.catalog

data class ActivityState(
    val id: String, val programId: String, val organizationId: String,
    val title: String, val program: String, val organization: String, val summary: String,
    val current: Boolean, val status: String, val participation: String, val date: String,
    val fields: List<Pair<String, String>>, val source: String, val application: String?,
    val checked: String, val sourceNote: String, val programSaved: Boolean, val organizationSaved: Boolean,
    val report: String?,
)
data class SavedGroupState(val id: String, val title: String, val organization: Boolean)
data class CatalogState(
    val activities: List<ActivityState> = emptyList(), val savedGroups: List<SavedGroupState> = emptyList(),
    val loading: Boolean = false, val writing: Boolean = false, val loaded: Boolean = false,
    val cached: Boolean = false, val generatedAt: String? = null, val error: String? = null,
    val storageReady: Boolean = false, val storageError: String? = null,
)
