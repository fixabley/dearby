package com.dearby.nativeapp.widgets.activity.activityCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.TealFill

/**
 * 오늘 내 행사 (2026-10-26 사용자 결정): the nearest 진행 중 내 활동 whose 활동 일정 is on today or starts within 7 days. [badge] is `오늘` or
 * `D-n`, [name] the activity, [body] the next 찜 session (`14:50 · 트랙 B · 그랜드볼룸 B · 세션 제목`, `지금 · …` while it runs) or the current/next
 * block, [others] how many more activities qualify (`외 n개`). [scheduleId] is the block whose 트랙·세션 시간표 the card opens, null when the
 * card opens the progress page instead; [favoritesFirst] starts that sheet on 찜한 세션.
 */
data class TodayEventState(val activityId: String, val name: String, val badge: String, val body: String, val today: Boolean, val others: Int,
    val scheduleId: String? = null, val favoritesFirst: Boolean = false)

/**
 * The 발견 top card for [state] (discovery card look, Material 3 `Card`): the badge and the activity name read as `오늘 · {활동 이름}`, the body
 * line under them, and a secondary `명함 QR` button ([qr]) that is its own tap; the rest of the card is [open].
 */
@Composable fun TodayEventCard(state: TodayEventState, open: () -> Unit, qr: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = if (state.today) TealFill to Color.White else MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    Card(open, modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.badge, Modifier.background(container, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(state.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.others > 0) Text("외 ${state.others}개", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(state.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(qr, Modifier.heightIn(min = 40.dp)) {
                    Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("명함 QR")
                }
            }
        }
    }
}
