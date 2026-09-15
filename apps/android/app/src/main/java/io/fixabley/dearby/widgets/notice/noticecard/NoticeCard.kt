package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardState
import io.fixabley.dearby.shared.ui.InformationRow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import io.fixabley.dearby.R
import io.fixabley.dearby.entities.notice.ui.NoticeClassification

@Composable
internal fun NoticeCard(
    notice: NoticeCardState, position: String,
    save: () -> Unit, showDetail: () -> Unit, onOpenMap: (NoticeVenue) -> Unit = {},
) {
    val currentSave by rememberUpdatedState(save)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.large, vertical = Spacing.small)) {
        // Compact changes spacing only; all facts remain reachable inside the card.
        val compact = maxHeight / LocalDensity.current.fontScale < 500.dp
        OutlinedCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(if (compact) Spacing.large else Spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.small else Spacing.large)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .pointerInput(notice.id) { detectTapGestures(onDoubleTap = { currentSave() }) }
                    .semantics { customActions = listOf(CustomAccessibilityAction("조직 즐겨찾기에 저장") { currentSave(); true }) }
                    .testTag("activity.${notice.id}"),
                    verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.small else Spacing.large)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("공고 샘플", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(position, style = MaterialTheme.typography.labelMedium)
                    }
                    NoticeClassification(notice.classification,
                        Modifier.testTag("classification.${notice.id}"),
                        maxLines = Int.MAX_VALUE)
                    Text(notice.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    InformationRow("참여 대상", notice.targetUser)
                    notice.schedules.forEachIndexed { index, schedule ->
                        CardScheduleRow(schedule, "card.schedule.${notice.id}.$index", onOpenMap)
                    }
                    if (notice.hasIssues) {
                        Text("확인이 필요한 정보가 있어요", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    Column(Modifier.weight(1f)) {
                        Text("관심 조직", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(notice.organizationName ?: "저장할 조직 확인 중", style = MaterialTheme.typography.titleMedium)
                    }
                    notice.organizationName?.let { name ->
                        NoticeCardSaveButton(notice.saved, name, save, Modifier.testTag("save.${notice.id}"))
                    }
                    OutlinedIconButton(onClick = showDetail, modifier = Modifier.testTag("details.${notice.id}")) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = "공고 정보 · 출처 보기")
                    }
                }
            }
        }
    }
}
