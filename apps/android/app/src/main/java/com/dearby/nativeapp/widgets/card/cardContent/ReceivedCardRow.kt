package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** 받은 명함 목록 한 줄. 화면이 명함 모델을 이름·직무·첫 활동 값으로 바꿔 넘기고, 누르면 [open]으로 상세를 연다. */
@Composable fun ReceivedCardRow(name: String, job: String, open: () -> Unit, modifier: Modifier = Modifier, activityTitle: String? = null, otherActivityCount: Int = 0) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = open).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Teal.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1), color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (job.isNotBlank()) Text(job, color = Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (activityTitle != null) DearbyTogetherActivityLabel(activityTitle, otherCount = otherActivityCount)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Quiet)
    }
}
