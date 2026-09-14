package io.fixabley.dearby.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import org.junit.Assert.*
import org.junit.Test

class CalendarEditorTest {
    private val draft = CalendarDraft("신청 [신청 마감]", "신청 URL: https://example.org/apply\n원문: https://example.org/source", "", 1789430400000, 1789516800000, true, "Asia/Seoul")

    @Test fun explicitEditorRequestHasNoPermissionsInviteesOrAutomaticSaveExtras() {
        val requests = mutableListOf<Intent>()
        openCalendarEditor(draft, { requests.add(it) }, { fail("Available") })
        val intent = requests.single()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertNull(intent.`package`)
        assertNull(intent.component)
        assertEquals(draft.title, intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(draft.description, intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals(draft.location, intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals(draft.beginsAtMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1))
        assertEquals(draft.endsAtMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1))
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals("UTC", intent.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
        assertEquals(setOf(CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.EXTRA_EVENT_BEGIN_TIME,
            CalendarContract.EXTRA_EVENT_END_TIME, CalendarContract.EXTRA_EVENT_ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.EVENT_END_TIMEZONE), intent.extras!!.keySet())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.READ_CALENDAR"))
        assertFalse(permissions.contains("android.permission.WRITE_CALENDAR"))
        val timed = calendarInsertIntent(draft.copy(allDay = false))
        assertEquals("Asia/Seoul", timed.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test fun absentOrBlockedCalendarEditorShowsFeedbackWithoutCrashing() {
        var feedback = 0
        for (error in listOf(ActivityNotFoundException(), SecurityException()))
            openCalendarEditor(draft, { throw error }, { feedback++ })
        assertEquals(2, feedback)
    }
}
