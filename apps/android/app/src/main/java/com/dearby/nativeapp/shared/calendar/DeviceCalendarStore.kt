package com.dearby.nativeapp.shared.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.coroutineContext

data class DeviceCalendarOption(val id: Long, val title: String)
data class DeviceBusyTime(val start: Long, val end: Long)

// Calendar Provider encodes all-day dates at UTC midnight; interpret those dates in
// the device zone, including 23/25-hour DST days, before testing time intersection.
fun calendarBusyTime(start: Long, end: Long, allDay: Boolean, zone: ZoneId): DeviceBusyTime =
    if (!allDay) DeviceBusyTime(start, end) else DeviceBusyTime(
        Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),
        Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(),
    )

class DeviceCalendarStore(private val context: Context) {
    fun authorized(): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    suspend fun calendars(): List<DeviceCalendarOption> = withContext(Dispatchers.IO) {
        checkPermission()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.VISIBLE} = ?", arrayOf("1"), null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) { coroutineContext.ensureActive(); add(DeviceCalendarOption(cursor.getLong(0), cursor.getString(1).orEmpty().ifBlank { "캘린더" })) } }
        }?.also { checkPermission() } ?: error("Calendar query unavailable")
    }
    suspend fun busy(start: Long, end: Long, calendarIds: Set<Long>): List<DeviceBusyTime> {
        val result = mutableListOf<DeviceBusyTime>()
        var cursor = start
        while (cursor < end) {
            coroutineContext.ensureActive()
            val upper = if (cursor > Long.MAX_VALUE - 366L * 86_400_000) end else minOf(cursor + 366L * 86_400_000, end)
            result += readWindow(cursor, upper, calendarIds)
            cursor = upper
        }
        return result.distinct()
    }
    private suspend fun readWindow(start: Long, end: Long, calendarIds: Set<Long>): List<DeviceBusyTime> = withContext(Dispatchers.IO) {
        checkPermission()
        require(calendarIds.isNotEmpty())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 172_800_000L)
            ContentUris.appendId(it, end + 172_800_000L)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.STATUS, CalendarContract.Instances.SELF_ATTENDEE_STATUS)
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})"
        val zone = ZoneId.systemDefault()
        context.contentResolver.query(uri, columns, selection, calendarIds.map { it.toString() }.toTypedArray(), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    if (cursor.getInt(3) == CalendarContract.Events.AVAILABILITY_FREE ||
                        cursor.getInt(4) == CalendarContract.Events.STATUS_CANCELED ||
                        cursor.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val time = calendarBusyTime(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2) != 0, zone)
                    if (time.start < time.end && time.start < end && start < time.end) add(time)
                }
            }
        }?.also { checkPermission() } ?: error("Calendar query unavailable")
    }
    private fun checkPermission() { if (!authorized()) throw SecurityException("Calendar permission required") }
}
