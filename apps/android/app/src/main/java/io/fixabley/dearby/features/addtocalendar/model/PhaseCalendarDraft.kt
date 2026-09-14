package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.activitycatalog.model.ActivityScheduleDetail
import io.fixabley.dearby.entities.activitycatalog.model.ActivityDetail
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun phaseCalendarDraft(notice: ActivityDetail, schedule: ActivityScheduleDetail): CalendarDraft? {
    val phase = schedule.period
    val period = calendarPeriod(phase.startsAt, phase.startsOn, phase.endsAt, phase.endsOn,
        phase.timezone, allowEndOnly = false) ?: return null
    val online = phase.mode == "online"
    val venues = schedule.locations
    val place = if (online) "온라인" else venues.joinToString(" / ") { venue ->
        listOfNotNull(venue.name?.takeIf { it.isNotBlank() }, venue.address?.takeIf { it.isNotBlank() })
            .distinct().joinToString(" · ").ifBlank { "장소 미확인" }
    }.ifBlank { "장소 미확인" }
    val maps = venues.mapNotNull { venue ->
        venue.coordinates?.takeIf { it.isValid }?.let { coordinates ->
            val point = URLEncoder.encode("${coordinates.latitude},${coordinates.longitude}", StandardCharsets.UTF_8.name())
            "지도 (${venue.displayName}): https://www.google.com/maps/search/?api=1&query=$point"
        }
    }
    val notes = listOfNotNull(
        notice.aiDescription, phase.summary,
        "시작: ${phase.startsAt ?: phase.startsOn ?: "미확인"}",
        "종료: ${phase.endsAt ?: phase.endsOn ?: "미확인"}",
        "시간대: ${phase.timezone}",
        if (period.allDay) "날짜 기준 종일 초안입니다. 정확한 시각은 위 안내를 확인해 주세요." else null,
        "장소: $place",
        if (online) calendarWebUrl(phase.onlineUrl)?.let { "온라인 URL: $it" } else null,
        if (maps.isNotEmpty()) "층·호실은 장소 안내를 확인해 주세요." else null,
        calendarWebUrl(notice.sourceURL)?.let { "원문: $it" },
    ) + maps
    return CalendarDraft("${notice.title} [${phase.label}]", notes.joinToString("\n"), place,
        period.begin, period.end, period.allDay, phase.timezone)
}
