package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.fixabley.dearby.entities.activitycatalog.model.ActivityPhase
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.shared.ui.NoticeFact

@Composable
internal fun ActivityScheduleSection(phase: ActivityPhase, draft: CalendarDraft?, index: Int, onAdd: (CalendarDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoticeFact("활동 일정", phase.summary)
        AddToCalendarButton(draft, "${phase.label} 캘린더에 추가", onAdd, Modifier.testTag("calendar.phase.$index"))
    }
}
