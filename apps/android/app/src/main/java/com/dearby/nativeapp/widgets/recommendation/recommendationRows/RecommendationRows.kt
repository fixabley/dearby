package com.dearby.nativeapp.widgets.recommendation.recommendationRows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dearby.nativeapp.shared.ui.InitialAvatar

/** One recommended activity: [subtitle] is `프로그램 · 기관`, [reasons] the server's short phrases (`데이터·AI`, `11월 7일 마감`). */
data class RecommendationItemState(val id: String, val title: String, val subtitle: String, val reasons: List<String>, val logoUrl: String?)

/** A recommendation as a Material list row with the program logo and reason chips; [open] null makes it display-only (the funnel's preview). */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun RecommendationRow(item: RecommendationItemState, open: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = if (open != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = open) else Modifier,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Reasons explain, they do not filter: plain labels in the chip shape, not FilterChips.
                if (item.reasons.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.reasons.forEach { reason ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(reason, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        leadingContent = {
            if (item.logoUrl != null) AsyncImage(item.logoUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            else InitialAvatar(item.subtitle, 40.dp, shape = RoundedCornerShape(10.dp))
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * 발견 탭 `추천` 묶음 (contract "발견 탭 노출"): a vertical list of three with `더 보기` (the rest inline) and the header's `설정`. When the funnel was skipped
 * ([personalized] false) only the one-line `나에게 맞는 활동 찾기` row shows, which reopens it.
 */
@Composable fun RecommendationSection(items: List<RecommendationItemState>, personalized: Boolean, open: (String) -> Unit, settings: () -> Unit) {
    if (!personalized) {
        ListItem(headlineContent = { Text("나에게 맞는 활동 찾기") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = settings),
            leadingContent = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("추천", Modifier.weight(1f).padding(start = 6.dp).semantics { heading() }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            TextButton(settings) { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("설정") }
        }
        if (items.isEmpty()) Text("지금 맞는 활동이 없어요. 설정을 바꿔 보세요.", Modifier.padding(horizontal = 6.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        (if (expanded) items else items.take(3)).forEach { RecommendationRow(it) { open(it.id) } }
        if (items.size > 3) TextButton({ expanded = !expanded }, Modifier.align(Alignment.CenterHorizontally)) { Text(if (expanded) "접기" else "더 보기") }
    }
}

/** 프로필 `추천` 묶음 (contract): `관심 분야·활동 종류` reopens the funnel; `지원 자격 정보` shows [eligibilitySummary] once entered. */
@Composable fun RecommendationProfileGroup(eligibilitySummary: String?, preferences: () -> Unit, eligibility: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("추천", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
        ListItem(headlineContent = { Text("지원 자격 정보") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = eligibility),
            supportingContent = { Text(eligibilitySummary ?: "입력하면 지원할 수 있는 활동만 추천해요") }, trailingContent = chevron, colors = colors)
        ListItem(headlineContent = { Text("관심 분야·활동 종류") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = preferences), trailingContent = chevron, colors = colors)
    }
}
