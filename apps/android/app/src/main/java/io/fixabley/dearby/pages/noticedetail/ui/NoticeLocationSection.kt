package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import io.fixabley.dearby.entities.notice.model.NoticeLocation
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.pages.noticedetail.model.detailPlace
import io.fixabley.dearby.pages.noticedetail.model.locationNote
import io.fixabley.dearby.shared.ui.ContentSection
import io.fixabley.dearby.shared.ui.DetailMetadata
import io.fixabley.dearby.shared.ui.MetadataRow

/** Only supplementary source context and venues not already displayed in a phase. */
@Composable
internal fun NoticeLocationSection(location: NoticeLocation, onOpenMap: (NoticeVenue) -> Unit,
    displayed: List<NoticeVenue> = emptyList()) {
    val venues = if (location.mode == "online") emptyList() else location.venues.filterNot { it in displayed }
    val note = locationNote(location.summary, if (location.mode == "online") emptyList() else location.venues)
    if (venues.isEmpty() && note == null) return
    ContentSection {
        note?.let { MetadataRow(painterResource(R.drawable.ic_place), it, "활동 장소: $it") }
        venues.forEachIndexed { index, venue ->
            val place = detailPlace(venue)
            DetailMetadata(painterResource(R.drawable.ic_place), place.name, place.detail, place.description) {
                VenueMapButton(venue, onOpenMap, Modifier.testTag("venue.map.$index"))
            }
        }
    }
}
