package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.notice.model.NoticeLocation
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.shared.ui.MetadataRow
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import io.fixabley.dearby.shared.ui.ContentSection

@Composable
internal fun NoticeLocationSection(location: NoticeLocation, onOpenMap: (NoticeVenue) -> Unit) {
    ContentSection {
        MetadataRow(painterResource(R.drawable.ic_place), location.summary, "활동 장소: ${location.summary}")
        if (location.mode != "online") {
            location.venues.forEachIndexed { index, venue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(listOfNotNull(venue.name, venue.address).distinct().joinToString(" · "),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    VenueMapButton(venue, onOpenMap, Modifier.testTag("venue.map.$index"))
                }
            }
            if (location.venues.any { it.canOpenMap }) {
                Text("층·호실은 장소 안내를 확인해 주세요.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
