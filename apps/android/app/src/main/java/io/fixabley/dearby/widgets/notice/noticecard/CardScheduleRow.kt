package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.shared.ui.theme.Spacing

@Composable
internal fun CardScheduleRow(schedule: CardScheduleState, tag: String, onOpenMap: (NoticeVenue) -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().testTag(tag), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(schedule.name, Modifier.weight(0.27f), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(0.73f).drawBehind {
            val x = -Spacing.small.toPx() / 2
            drawLine(dividerColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    schedule.dateLines.forEachIndexed { index, line ->
                        Text(line, Modifier.testTag("$tag.date.$index"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            schedule.places.forEachIndexed { index, place ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        place.lines.forEachIndexed { lineIndex, line ->
                            CardPlaceLine(line, Modifier.testTag("$tag.place.$index.$lineIndex"))
                        }
                    }
                    place.mapVenue?.let { venue ->
                        IconButton(onClick = { onOpenMap(venue) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("$tag.map.$index")) {
                            Icon(painterResource(R.drawable.ic_map), contentDescription = "${schedule.name} · ${place.lines.joinToString(" · ")} 지도 열기")
                        }
                    }
                }
            }
        }
    }
}
