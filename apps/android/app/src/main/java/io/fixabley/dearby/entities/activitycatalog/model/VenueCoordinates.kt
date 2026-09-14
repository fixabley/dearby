package io.fixabley.dearby.entities.activitycatalog.model

internal data class VenueCoordinates(val latitude: Double, val longitude: Double) {
    val isValid: Boolean get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}
