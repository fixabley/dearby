package io.fixabley.dearby.pages.noticedetail.ui

import io.fixabley.dearby.shared.ui.buttons.SecondaryButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.entities.notice.model.NoticeVenue

@Composable
internal fun VenueMapButton(venue: NoticeVenue, onOpenMap: (NoticeVenue) -> Unit, modifier: Modifier = Modifier) {
    if (venue.canOpenMap) {
        SecondaryButton(onClick = { onOpenMap(venue) }, modifier = modifier) {
            Text("${venue.displayName} 지도에서 보기")
        }
    }
}
