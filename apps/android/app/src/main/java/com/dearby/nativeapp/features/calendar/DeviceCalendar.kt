package com.dearby.nativeapp.features.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Busy times from the device calendar for the overlap check, read on the device only (contract #148):
 * titles and times are never sent or stored, and nothing is written. Needs READ_CALENDAR.
 */
suspend fun deviceBusyTimes(context: Context, start: Long, end: Long): List<BusyTimeState> = withContext(Dispatchers.IO) {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, start)
        ContentUris.appendId(it, end)
    }.build()
    val columns = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.AVAILABILITY)
    context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                if (cursor.getInt(2) == CalendarContract.Instances.AVAILABILITY_FREE) continue
                val busy = BusyTimeState(cursor.getLong(0), cursor.getLong(1))
                if (busy.start < busy.end) add(busy)
            }
        }
    }.orEmpty()
}
