package io.fixabley.dearby.entities.noticecatalog.model

internal data class NoticeLocation(
    val summary: String,
    val mode: String,
    val status: String,
    val venues: List<NoticeVenue>,
)
