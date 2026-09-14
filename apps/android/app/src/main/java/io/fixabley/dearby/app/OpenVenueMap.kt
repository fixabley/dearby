package io.fixabley.dearby.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import io.fixabley.dearby.entities.notice.model.NoticeVenue

internal fun venueMapIntent(venue: NoticeVenue): Intent? {
    val coordinates = venue.coordinates?.takeIf { it.isValid } ?: return null
    val point = "${coordinates.latitude},${coordinates.longitude}"
    val query = Uri.encode("$point(${venue.displayName})")
    // No package pinning: Android chooses among installed geo handlers.
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$query"))
}

internal fun openVenueMap(venue: NoticeVenue, startActivity: (Intent) -> Unit, onUnavailable: () -> Unit) {
    val intent = venueMapIntent(venue) ?: return
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    } catch (_: SecurityException) {
        onUnavailable()
    }
}
