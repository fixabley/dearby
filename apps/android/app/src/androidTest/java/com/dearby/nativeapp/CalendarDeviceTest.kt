package com.dearby.nativeapp

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.catalog.model.ScheduleModel
import com.dearby.nativeapp.features.calendar.CalendarConflictState
import com.dearby.nativeapp.shared.calendar.DeviceCalendarStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CalendarDeviceTest {
    @Test fun realProviderExpandsRecurrenceFiltersFreeAndClearsState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Explicit opt-in: never seed a real user's device.
        assumeTrue(InstrumentationRegistry.getArguments().getString("calendarFixture") == "true")
        val automation = instrumentation.uiAutomation
        val context = instrumentation.targetContext
        automation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val resolver = context.contentResolver
        val account = "dearby-calendar-test-${UUID.randomUUID()}"
        val calendarUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        var calendarId: Long? = null
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, account)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Dearby 검증용")
                put(CalendarContract.Calendars.CALENDAR_COLOR, -16744320)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.VISIBLE, 1); put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
            calendarId = ContentUris.parseId(checkNotNull(resolver.insert(calendarUri, values)))
            fun event(start: String, end: String?, free: Boolean, recurring: Boolean) {
                val data = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, "PRIVATE TEST TITLE")
                    put(CalendarContract.Events.DTSTART, Instant.parse(start).toEpochMilli())
                    put(CalendarContract.Events.EVENT_TIMEZONE, "Asia/Seoul")
                    put(CalendarContract.Events.AVAILABILITY, if (free) CalendarContract.Events.AVAILABILITY_FREE else CalendarContract.Events.AVAILABILITY_BUSY)
                    if (recurring) { put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=2"); put(CalendarContract.Events.DURATION, "PT1H") }
                    else put(CalendarContract.Events.DTEND, Instant.parse(end).toEpochMilli())
                }
                checkNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, data))
            }
            event("2026-10-23T05:30:00Z", null, false, true)
            event("2026-10-24T05:00:00Z", "2026-10-24T07:00:00Z", true, false)
            val store = DeviceCalendarStore(context)
            assertTrue(store.calendars().any { it.id == calendarId })
            val times = store.busy(Instant.parse("2026-10-24T05:00:00Z").toEpochMilli(), Instant.parse("2026-10-24T07:00:00Z").toEpochMilli(), setOf(calendarId))
            assertEquals(1, times.size)
            assertEquals(Instant.parse("2026-10-24T05:30:00Z").toEpochMilli(), times.single().start)
            val state = CalendarConflictState(listOf(ScheduleModel("s", "활동", "2026-10-24T05:00:00Z", "2026-10-24T07:00:00Z", "", "Asia/Seoul")), store)
            state.connect()
            state.calendars.filter { it.id != calendarId }.forEach { state.toggle(it.id) }
            state.compare()
            assertEquals("result", state.phase); assertEquals(1, state.overlaps.size)
            state.clear(); assertTrue(state.overlaps.isEmpty()); assertTrue(state.calendars.isEmpty()); assertTrue(state.selected.isEmpty())
        } finally {
            calendarId?.let { resolver.delete(calendarUri.buildUpon().appendPath(it.toString()).build(), null, null) }
            automation.dropShellPermissionIdentity()
        }
    }
}
