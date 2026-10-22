package com.dearby.nativeapp.features.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * `캘린더에 추가` (contract "내 캘린더에 추가", 2026-10-22): opens the calendar app's own "add event" screen prefilled; the user decides
 * there whether to save. The app never writes the calendar itself, so no permission is needed. [begin]/[end] are epoch millis
 * (Seoul midnights when [allDay]). False when no calendar app can take it.
 */
fun openCalendarInsert(context: Context, title: String, begin: Long, end: Long, allDay: Boolean, location: String?, description: String?): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay).putExtra(CalendarContract.Events.TITLE, title)
        .apply { location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }; description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) } })
    true
} catch (e: ActivityNotFoundException) { false }
