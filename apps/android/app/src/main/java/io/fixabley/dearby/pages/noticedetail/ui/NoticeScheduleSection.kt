package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.pages.noticedetail.model.NoticeScheduleState
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.shared.ui.NoticeFact

@Composable
internal fun NoticeScheduleSection(phase: NoticeScheduleState, draft: CalendarDraft?, index: Int, onAdd: (CalendarDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        NoticeFact("활동 일정", phase.period.summary)
        AddToCalendarButton(draft, "${phase.period.label} 캘린더에 추가", onAdd, Modifier.testTag("calendar.phase.$index"))
    }
}
