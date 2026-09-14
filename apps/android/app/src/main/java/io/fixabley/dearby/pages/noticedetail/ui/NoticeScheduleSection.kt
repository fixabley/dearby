package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.pages.noticedetail.model.NoticeScheduleState
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.shared.ui.InformationRow
import io.fixabley.dearby.shared.ui.ContentSection

@Composable
internal fun NoticeScheduleSection(phase: NoticeScheduleState, draft: CalendarDraft?, index: Int, onAdd: (CalendarDraft) -> Unit) {
    ContentSection {
        InformationRow("활동 일정", phase.period.summary)
        AddToCalendarButton(draft, "${phase.period.label} 캘린더에 추가", onAdd, Modifier.testTag("calendar.phase.$index"))
    }
}
