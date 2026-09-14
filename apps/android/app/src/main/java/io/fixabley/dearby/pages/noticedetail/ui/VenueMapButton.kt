package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.entities.activitycatalog.model.ActivityVenue

@Composable
internal fun VenueMapButton(venue: ActivityVenue, onOpenMap: (ActivityVenue) -> Unit, modifier: Modifier = Modifier) {
    if (venue.canOpenMap) {
        OutlinedButton(onClick = { onOpenMap(venue) }, modifier = modifier) {
            Text("${venue.displayName} 지도에서 보기")
        }
    }
}
