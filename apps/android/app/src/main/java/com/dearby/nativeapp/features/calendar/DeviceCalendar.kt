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
 * `READ_CALENDAR`; the caller checks it first. All-day and cancelled events are skipped: they do not occupy a time slot. Events
 * whose notes or URL carry [ownLink] (Dearby's own "캘린더에 추가", contract "반복 일정과 겹침 결과 묶음") are left out so an activity
 * never overlaps itself. Each instance keeps its event (an exception's original event) and that event's RRULE for grouping.
 * Nothing is kept after the list is returned, nothing goes to the server.
 */
class DeviceCalendarSource(private val resolver: ContentResolver, private val ownLink: String?) : BusyTimeSource {
    override fun busyTimes(from: Long, to: Long): List<BusyTimeState> {
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.ORIGINAL_ID, CalendarContract.Instances.RRULE, CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.STATUS)
        val cursor = CalendarContract.Instances.query(resolver, projection, from, to) ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getInt(3) == 1 || (!it.isNull(8) && it.getInt(8) == CalendarContract.Instances.STATUS_CANCELED)) continue
                    if (ownLink != null && it.getString(7)?.contains(ownLink) == true) continue
                    add(BusyTimeState(it.getLong(1), it.getLong(2), it.getString(0)?.ifBlank { null }, it.getString(5)?.ifBlank { null } ?: it.getLong(4).toString(), it.getString(6)?.ifBlank { null }))
                }
            }
        }
    }
}
