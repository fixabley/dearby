package io.fixabley.dearby.app.data

import io.fixabley.dearby.entities.notice.model.NoticeLocation
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.entities.notice.model.VenueCoordinates
import org.json.JSONObject

internal fun decodeNoticeLocation(json: JSONObject): NoticeLocation {
    val venues = json.optJSONArray("venues")
    return NoticeLocation(
        summary = json.getString("summary"),
        mode = json.optString("mode", "unknown"),
        status = json.optString("status", "unknown"),
        venues = (0 until (venues?.length() ?: 0)).mapNotNull { index ->
            val venue = venues?.optJSONObject(index) ?: return@mapNotNull null
            val coordinates = venue.optJSONObject("coordinates")
            // Reject partial values and coercions (strings/booleans), never fill absent axes with zero.
            val latitude = (coordinates?.opt("latitude") as? Number)?.toDouble()
            val longitude = (coordinates?.opt("longitude") as? Number)?.toDouble()
            NoticeVenue(
                phase = venue.opt("phase") as? String,
                name = venue.opt("name") as? String,
                address = venue.opt("address") as? String,
                coordinates = if (latitude != null && longitude != null) {
                    VenueCoordinates(latitude, longitude).takeIf { it.isValid }
                } else null,
            )
        },
    )
}
