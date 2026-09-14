package io.fixabley.dearby.entities.activitycatalog.model

internal data class ActivityLocation(
    val summary: String,
    val mode: String,
    val status: String,
    val venues: List<ActivityVenue>,
)
