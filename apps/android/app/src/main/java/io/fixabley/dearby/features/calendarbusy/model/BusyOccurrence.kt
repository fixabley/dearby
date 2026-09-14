package io.fixabley.dearby.features.calendarbusy.model

import io.fixabley.dearby.shared.ui.BusyInterval
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Only numeric provider fields are accepted, including OS-expanded recurring occurrences. */
internal data class BusyOccurrence(val begin: Long, val end: Long, val allDay: Boolean,
    val availability: Int, val status: Int, val selfStatus: Int) {
    fun interval(deviceZone: ZoneId): BusyInterval? {
        // CalendarContract: FREE=1, CANCELED=2, ATTENDEE_STATUS_DECLINED=2; tentative remains busy.
        if (availability == 1 || status == 2 || selfStatus == 2 || end <= begin) return null
        val startInstant = Instant.ofEpochMilli(begin)
        val endInstant = Instant.ofEpochMilli(end)
        val start = if (allDay) startInstant.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(deviceZone).toInstant() else startInstant
        val finish = if (allDay) endInstant.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay(deviceZone).toInstant() else endInstant
        return if (finish > start) BusyInterval(start, finish) else null
    }
}
