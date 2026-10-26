package com.dearby.nativeapp.widgets.activity.sessionCard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.SessionHeartButton
import com.dearby.nativeapp.shared.ui.TagChip
import com.dearby.nativeapp.shared.ui.TagLabel
import com.dearby.nativeapp.shared.ui.TrackColor

/** A track as a session card shows it: name, room and its palette colour (contract "트랙·세션 시간표"). */
data class TrackBadge(val name: String, val room: String?, val color: TrackColor)

/**
 * One session of a 시간표 (contract "트랙·세션 시간표"): track badge, at most two 분야 tags, title, speakers (`이름 · 소속`) and the 찜 heart
 * ([onHeart] null hides it). [time] is shown above the title when the card stands alone (나만의 시간표). [overlap] > 1 warns that this many
 * 찜한 세션 run at the same time. [bigTrack] makes the track name and room the card's lead line (나만의 시간표: where to go).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SessionCard(
    title: String, track: TrackBadge?, tags: List<TagLabel>, speakers: String?, favorited: Boolean, onHeart: (() -> Unit)?, open: () -> Unit,
    modifier: Modifier = Modifier, time: String? = null, overlap: Int = 0, bigTrack: Boolean = false,
) {
    OutlinedCard(open, modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    time?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
                    if (track != null) {
                        if (bigTrack) Text(listOfNotNull(track.name, track.room).joinToString(" · "), color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        else Surface(color = track.color.container, contentColor = track.color.content, shape = RoundedCornerShape(8.dp)) {
                            Text(listOfNotNull(track.name, track.room).joinToString(" · "), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (onHeart != null) SessionHeartButton(favorited, onHeart) else Spacer(Modifier.height(48.dp).width(12.dp))
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.take(2).forEach { TagChip(it) } }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                speakers?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (overlap > 1) OverlapNote("같은 시간 찜 ${overlap}개")
            }
        }
    }
}

/** 쉬는시간·점심 (`BREAK`·`MEAL`): a plain band across the row, no heart. */
@Composable fun SessionBand(title: String, meal: Boolean, modifier: Modifier = Modifier, time: String? = null) =
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (meal) Icons.Rounded.Restaurant else Icons.Rounded.Coffee, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOfNotNull(time, title).joinToString("  "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }

/** 찜 겹침 warning (contract "찜 겹침"). */
@Composable private fun OverlapNote(text: String, modifier: Modifier = Modifier) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
    }
