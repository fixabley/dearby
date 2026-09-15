package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.fixabley.dearby.R
import io.fixabley.dearby.pages.noticedetail.model.NoticeScheduleState
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.pages.noticedetail.model.detailPlace
import io.fixabley.dearby.shared.ui.MetadataRow
import io.fixabley.dearby.shared.ui.DetailMetadata
import io.fixabley.dearby.shared.ui.LinkCard
import io.fixabley.dearby.shared.ui.DayTimeline
import io.fixabley.dearby.shared.ui.ContentSection

@Composable
internal fun NoticeScheduleSection(phase: NoticeScheduleState, draft: CalendarDraft?, index: Int, onAdd: (CalendarDraft) -> Unit, onOpenMap: (NoticeVenue) -> Unit = {}, onOpenLink: (String) -> Unit = {}) {
    ContentSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(phase.title, Modifier.weight(1f).semantics { heading() }.testTag("schedule.title.$index"),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            AddToCalendarButton(draft, "${phase.title} 캘린더에 추가", onAdd, Modifier.testTag("calendar.phase.$index"))
        }
        phase.dateDetails.lines.forEachIndexed { lineIndex, line ->
            DetailMetadata(painterResource(R.drawable.ic_calendar), line.date, line.time, phase.dateDescription,
                Modifier.testTag("schedule.date.$index.$lineIndex"))
        }
        if (phase.period.mode == "online" || phase.locations.isEmpty()) {
            DetailMetadata(painterResource(R.drawable.ic_place), phase.placeText,
                null,
                "${phase.title} 장소: ${phase.placeDescription}", Modifier.testTag("schedule.place.$index"))
        } else phase.locations.forEachIndexed { venueIndex, venue ->
            val place = detailPlace(venue)
            DetailMetadata(painterResource(R.drawable.ic_place), place.name, place.detail,
                "${phase.title} 장소: ${place.description}", Modifier.testTag("schedule.place.$index.$venueIndex")) {
                VenueMapButton(venue, onOpenMap, Modifier.testTag("schedule.map.$index.$venueIndex"))
            }
        }
        phase.link?.let { link ->
            LinkCard(link.domain, link.url, { onOpenLink(link.url) }, Modifier.testTag("schedule.link.$index"))
        }
        phase.timeline?.let { DayTimeline(it, phase.title, Modifier.testTag("schedule.timeline.$index")) }
            ?: Text("시작·종료 시각이 모두 확인되어야 시간표를 표시할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        Text(if (phase.period.timezone == "Asia/Seoul") "한국 시간" else phase.period.timezone,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "일정 · light", widthDp = 360, showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "일정 · dark", widthDp = 360, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "일정 · 2×", widthDp = 360, fontScale = 2f, showBackground = true)
@Composable
private fun SchedulePreview() {
    io.fixabley.dearby.shared.ui.theme.DearbyTheme(dynamicColor = false) {
        NoticeScheduleSection(NoticeScheduleState(
            io.fixabley.dearby.entities.notice.model.NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online"),
            emptyList()), CalendarDraft("온라인 예선", "", "온라인", 0, 0, true, "Asia/Seoul"), 0, {})
    }
}
