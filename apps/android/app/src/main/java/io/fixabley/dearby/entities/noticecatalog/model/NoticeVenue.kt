package io.fixabley.dearby.entities.noticecatalog.model

internal data class NoticeVenue(
    val phase: String?,
    val name: String?,
    val address: String?,
    val coordinates: VenueCoordinates?,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() }
        ?: address?.takeIf { it.isNotBlank() } ?: "행사 장소"
    val canOpenMap: Boolean get() = coordinates?.isValid == true
}
