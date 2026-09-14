package io.fixabley.dearby.features.addtocalendar.model

import io.fixabley.dearby.entities.notice.model.NoticeModel

internal fun applicationCalendarDraft(notice: NoticeModel): CalendarDraft? {
    val application = notice.applicationInformation
    val period = calendarPeriod(application.opensAt, application.opensOn, application.closesAt,
        application.closesOn, application.timezone, allowEndOnly = true) ?: return null
    val description = calendarWebUrl(notice.sourceURL).orEmpty()
    return CalendarDraft("${notice.title} [${if (period.endOnly) "신청 마감" else "신청 기간"}]",
        description, "", period.begin, period.end, period.allDay, application.timezone)
}
