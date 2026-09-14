package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.entities.notice.model.NoticeModel

internal fun phaseCalendarDraft(notice: NoticeModel, phase: NoticePhase): CalendarDraft? {
    val period = calendarPeriod(phase.startsAt, phase.startsOn, phase.endsAt, phase.endsOn,
        phase.timezone, allowEndOnly = false) ?: return null
    val online = phase.mode == "online"
    val venues = notice.venuesFor(phase)
    val place = if (online) "온라인" else venues.joinToString(" / ") { venue ->
        listOfNotNull(venue.name?.takeIf { it.isNotBlank() }, venue.address?.takeIf { it.isNotBlank() })
            .distinct().joinToString(" · ").ifBlank { "장소 미확인" }
    }.ifBlank { "장소 미확인" }
    val description = calendarWebUrl(notice.sourceURL).orEmpty()
    return CalendarDraft("${notice.title} [${phase.label}]", description, place,
        period.begin, period.end, period.allDay, phase.timezone)
}
