package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.entities.noticecatalog.model.NoticeDetail
import io.fixabley.dearby.entities.noticecatalog.model.NoticeVenue
import io.fixabley.dearby.shared.ui.NoticeFact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeDetailSheet(notice: NoticeDetail, onDismiss: () -> Unit, onOpenSource: (String) -> Unit, onOpenMap: (NoticeVenue) -> Unit, applicationDraft: CalendarDraft?, phaseDrafts: List<CalendarDraft?>, onAddToCalendar: (CalendarDraft) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("notice.detail"),
            contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text(notice.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text(notice.aiDescription) }
            item { NoticeIdentity(notice) }
            item { HorizontalDivider() }
            item { NoticeFact("참여 대상", notice.targetUser) }
            item { NoticeFact("참여 조건", notice.participationCondition) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NoticeFact("신청 기간", notice.applicationInformation.summary)
                    AddToCalendarButton(applicationDraft, "신청 기간 캘린더에 추가", onAddToCalendar,
                        Modifier.testTag("calendar.application"))
                }
            }
            itemsIndexed(notice.schedules) { index, phase -> NoticeScheduleSection(phase, phaseDrafts.getOrNull(index), index, onAddToCalendar) }
            item { NoticeLocationSection(notice.location, onOpenMap) }
            items(notice.benefits) { NoticeFact("혜택", it) }
            items(notice.issues) { NoticeFact("확인 필요", it) }
            item {
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(onClick = { onOpenSource(notice.sourceURL) }) { Text("원문 공고 열기") }
            }
        }
    }
}
