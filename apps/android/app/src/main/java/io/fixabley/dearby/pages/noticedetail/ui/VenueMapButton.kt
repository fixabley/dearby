package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.entities.notice.model.NoticeVenue

@Composable
internal fun VenueMapButton(venue: NoticeVenue, onOpenMap: (NoticeVenue) -> Unit, modifier: Modifier = Modifier) {
    if (venue.canOpenMap) {
        IconButton(onClick = { onOpenMap(venue) }, modifier = modifier) {
            Icon(painterResource(R.drawable.ic_place), contentDescription = "${venue.displayName} 지도에서 보기")
        }
    }
}
