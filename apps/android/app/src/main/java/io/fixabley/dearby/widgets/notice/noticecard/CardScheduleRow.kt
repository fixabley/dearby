package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun CardScheduleRow(schedule: CardScheduleState, tag: String, onOpenMap: (NoticeVenue) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(tag), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(schedule.name, Modifier.weight(0.27f), style = MaterialTheme.typography.labelLarge)
        VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp)
        Column(Modifier.weight(0.73f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(schedule.dateText, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            schedule.places.forEachIndexed { index, place ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    place.mapVenue?.let { venue ->
                        IconButton(onClick = { onOpenMap(venue) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("$tag.map.$index")) {
                            Icon(painterResource(R.drawable.ic_map), contentDescription = "${schedule.name} · ${place.text} 지도 열기")
                        }
                    }
                }
            }
        }
    }
}
