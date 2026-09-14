package io.fixabley.dearby.features.calendarbusy.api

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.CalendarContract
import io.fixabley.dearby.features.calendarbusy.model.BusyOccurrence
import io.fixabley.dearby.shared.ui.BusyInterval
import io.fixabley.dearby.shared.ui.mergedBusy
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** READ_CALENDAR only. Query and cursor lifetime stay off main; no personal string projection/logging. */
internal class AndroidBusyProvider(context: Context,
    private val deviceZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val permissionCheck: () -> BusyPermission = {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) BusyPermission.Granted
        else {
            if (context.packageManager.isPermissionRevokedByPolicy(Manifest.permission.READ_CALENDAR, context.packageName))
                BusyPermission.Restricted else BusyPermission.NotGranted
        }
    },
    private val queryCursor: (Uri, Array<String>, String, CancellationSignal) -> Cursor? = { uri, projection, selection, signal ->
        context.contentResolver.query(uri, projection, selection, null, CalendarContract.Instances.BEGIN + " ASC", signal)
    },
) : BusyProvider {
    override fun permission() = permissionCheck()
    override suspend fun read(query: BusyQuery): List<BusyInterval> = withContext(Dispatchers.IO) {
        if (permission() != BusyPermission.Granted) throw SecurityException()
        val zone = deviceZone()
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val result = mutableListOf<BusyInterval>()
                val window = query.window
                // Timed instances use real UTC bounds. Floating all-day dates need a separate UTC-date query.
                val floatingStart = window.start.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                val floatingEnd = window.end.minusNanos(1).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                listOf(Triple(window.start, window.end, 0), Triple(floatingStart, floatingEnd, 1)).forEach { (start, end, allDay) ->
                    signal.throwIfCanceled()
                    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                        ContentUris.appendId(it, start.toEpochMilli()); ContentUris.appendId(it, end.toEpochMilli())
                    }.build()
                    val selection = "allDay=$allDay AND (availability IS NULL OR availability!=1) AND (eventStatus IS NULL OR eventStatus!=2) AND (selfAttendeeStatus IS NULL OR selfAttendeeStatus!=2)"
                    val cursor = queryCursor(uri, PROJECTION.copyOf(), selection, signal) ?: error("Calendar query unavailable")
                    cursor.use {
                        while (it.moveToNext()) {
                            signal.throwIfCanceled()
                            BusyOccurrence(it.getLong(0), it.getLong(1), it.getInt(2) != 0,
                                it.getInt(3), it.getInt(4), it.getInt(5)).interval(zone)?.let(result::add)
                        }
                    }
                }
                if (permission() != BusyPermission.Granted) throw SecurityException()
                continuation.resume(mergedBusy(result, query.window))
            } catch (failure: Exception) { continuation.resumeWithException(failure) }
        }
    }
    companion object {
        val PROJECTION = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Events.ALL_DAY, CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.STATUS, CalendarContract.Events.SELF_ATTENDEE_STATUS)
    }
}
