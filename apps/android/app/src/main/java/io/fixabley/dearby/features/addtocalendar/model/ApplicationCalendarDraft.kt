package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.activitycatalog.model.Notice

internal fun applicationCalendarDraft(notice: Notice): CalendarDraft? {
    val application = notice.application
    val period = calendarPeriod(application.opensAt, application.opensOn, application.closesAt,
        application.closesOn, application.timezone, allowEndOnly = true) ?: return null
    val description = listOfNotNull(
        application.summary,
        "시작: ${application.opensAt ?: application.opensOn ?: "미확인"}",
        "마감: ${application.closesAt ?: application.closesOn ?: "미확인"}",
        "시간대: ${application.timezone}",
        if (period.allDay) "날짜 기준 종일 초안입니다. 정확한 시각은 위 안내를 확인해 주세요." else null,
        calendarWebUrl(application.url)?.let { "신청 URL: $it" },
        if (calendarWebUrl(application.url) == null) "신청 URL: 미확인" else null,
        calendarWebUrl(notice.sourceUrl)?.let { "원문: $it" },
    ).joinToString("\n")
    return CalendarDraft("${notice.title} [${if (period.endOnly) "신청 마감" else "신청 기간"}]",
        description, "", period.begin, period.end, period.allDay, application.timezone)
}
