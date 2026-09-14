package io.fixabley.dearby.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft

internal fun calendarInsertIntent(draft: CalendarDraft): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
    putExtra(CalendarContract.Events.TITLE, draft.title)
    putExtra(CalendarContract.Events.DESCRIPTION, draft.description)
    putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.beginsAtMillis)
    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endsAtMillis)
    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, draft.allDay)
    putExtra(CalendarContract.Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else draft.timezone)
    putExtra(CalendarContract.Events.EVENT_END_TIMEZONE, if (draft.allDay) "UTC" else draft.timezone)
}

internal fun openCalendarEditor(draft: CalendarDraft, startActivity: (Intent) -> Unit, onUnavailable: () -> Unit) {
    try { startActivity(calendarInsertIntent(draft)) }
    catch (_: ActivityNotFoundException) { onUnavailable() }
    catch (_: SecurityException) { onUnavailable() }
}
