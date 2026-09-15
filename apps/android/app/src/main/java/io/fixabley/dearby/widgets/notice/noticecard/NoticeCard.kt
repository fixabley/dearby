package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardState
import io.fixabley.dearby.shared.ui.InformationRow
import io.fixabley.dearby.shared.ui.MetadataRow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import io.fixabley.dearby.R
import io.fixabley.dearby.entities.notice.ui.NoticeClassification

@Composable
internal fun NoticeCard(
    notice: NoticeCardState, position: String,
    save: () -> Unit, showDetail: () -> Unit,
) {
    val currentSave by rememberUpdatedState(save)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.large, vertical = Spacing.small)) {
        // Reserve room for actions as the system font grows; full facts remain in the sheet.
        val compact = maxHeight / LocalDensity.current.fontScale < 500.dp
        OutlinedCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(if (compact) Spacing.large else Spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.small else Spacing.large)) {
                Column(Modifier.weight(1f).fillMaxWidth()
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
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(notice.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        HorizontalDivider()
                        InformationRow("참여 대상", notice.targetUser, maxLines = 2)
                    }
                    MetadataRow(painterResource(R.drawable.ic_calendar), notice.applicationDateText,
                        "신청 기간: ${notice.applicationSummary}", maxLines = if (compact) 1 else 2)
                    MetadataRow(painterResource(R.drawable.ic_place), notice.locationSummary,
                        "활동 장소: ${notice.locationSummary}", maxLines = if (compact) 1 else 2)
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
