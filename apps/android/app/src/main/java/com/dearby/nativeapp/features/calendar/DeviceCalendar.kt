package com.dearby.nativeapp.features.calendar

import android.content.ContentResolver
import android.provider.CalendarContract

/**
 * Busy times between two instants (epoch millis). The device calendar is the only source (contract "기기 캘린더 겹침",
 * 2026-10-06; wired 2026-10-19). The interface keeps the comparison testable without a device.
 */
fun interface BusyTimeSource {
    fun busyTimes(from: Long, to: Long): List<BusyTimeState>
}

/**
 * Reads event instances from the device calendars with `CalendarContract.Instances` for the asked window only. Needs
 * `READ_CALENDAR`; the caller checks it first. All-day events are skipped: they do not occupy a time slot. Nothing is kept
 * after the list is returned, nothing goes to the server.
 */
class DeviceCalendarSource(private val resolver: ContentResolver) : BusyTimeSource {
    override fun busyTimes(from: Long, to: Long): List<BusyTimeState> {
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        val cursor = CalendarContract.Instances.query(resolver, projection, from, to) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getInt(3) == 1) continue
                    add(BusyTimeState(it.getLong(1), it.getLong(2), it.getString(0)?.ifBlank { null }))
                }
            }
        }
    }
}
