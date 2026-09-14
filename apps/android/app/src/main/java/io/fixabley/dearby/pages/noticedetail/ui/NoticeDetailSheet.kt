package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailState
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.shared.ui.InformationRow
import io.fixabley.dearby.shared.ui.ContentSection
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeDetailSheet(notice: NoticeDetailState, onDismiss: () -> Unit, onOpenSource: (String) -> Unit, onOpenMap: (NoticeVenue) -> Unit, onAddToCalendar: (CalendarDraft) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = Spacing.extraLarge).testTag("notice.detail"),
            contentPadding = PaddingValues(bottom = Spacing.section), verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            item { Text(notice.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text(notice.aiDescription) }
            item { NoticeIdentity(notice) }
            item { HorizontalDivider() }
            item { InformationRow("참여 대상", notice.targetUser) }
            item { InformationRow("참여 조건", notice.participationCondition) }
            item {
                ContentSection {
                    InformationRow("신청 기간", notice.applicationInformation.summary)
                    AddToCalendarButton(notice.applicationDraft, "신청 기간 캘린더에 추가", onAddToCalendar,
                        Modifier.testTag("calendar.application"))
                }
            }
            itemsIndexed(notice.schedules) { index, phase -> NoticeScheduleSection(phase, notice.phaseDrafts.getOrNull(index), index, onAddToCalendar) }
            item { NoticeLocationSection(notice.location, onOpenMap) }
            items(notice.benefits) { InformationRow("혜택", it) }
            items(notice.issues) { InformationRow("확인 필요", it) }
            item {
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                PrimaryButton(onClick = { onOpenSource(notice.sourceURL) }) { Text("원문 공고 열기") }
            }
        }
    }
}
