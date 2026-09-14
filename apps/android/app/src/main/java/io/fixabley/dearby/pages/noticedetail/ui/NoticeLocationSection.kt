package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.entities.noticecatalog.model.NoticeLocation
import io.fixabley.dearby.entities.noticecatalog.model.NoticeVenue
import io.fixabley.dearby.shared.ui.NoticeFact

@Composable
internal fun NoticeLocationSection(location: NoticeLocation, onOpenMap: (NoticeVenue) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NoticeFact("활동 장소", location.summary)
        if (location.mode != "online") {
            location.venues.forEachIndexed { index, venue ->
                VenueMapButton(venue, onOpenMap, Modifier.testTag("venue.map.$index"))
            }
            if (location.venues.any { it.canOpenMap }) {
                Text("층·호실은 장소 안내를 확인해 주세요.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
