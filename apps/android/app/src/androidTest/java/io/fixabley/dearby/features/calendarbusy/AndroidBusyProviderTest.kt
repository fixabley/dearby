package io.fixabley.dearby.features.calendarbusy

import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.features.calendarbusy.api.*
import io.fixabley.dearby.shared.ui.BusyInterval
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** In-memory numeric fixture only: never queries the device CalendarProvider or grants permission. */
class AndroidBusyProviderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = LocalDate.of(2026, 9, 15).atStartOfDay(zone).toInstant()
    private val window = BusyInterval(start, start.plusSeconds(86400))
    @Test fun numericProjectionSqlNullFilteringAndFloatingAllDay() = runBlocking {
        SQLiteDatabase.create(null).use { db ->
            db.execSQL("CREATE TABLE instances(begin INTEGER, end INTEGER, allDay INTEGER, availability INTEGER, eventStatus INTEGER, selfAttendeeStatus INTEGER, visible INTEGER)")
            fun row(s: Instant, e: Instant, all: Int = 0, availability: Int? = 0, status: Int? = 0, self: Int? = 0) {
                db.execSQL("INSERT INTO instances VALUES(?,?,?,?,?,?,1)", arrayOf(s.toEpochMilli(), e.toEpochMilli(), all, availability, status, self))
            }
            row(start.plusSeconds(3600), start.plusSeconds(7200), availability = null, status = null, self = null)
            row(start.plusSeconds(10800), start.plusSeconds(14400), availability = 2)
            row(start.plusSeconds(18000), start.plusSeconds(21600), availability = 1)
            row(start.plusSeconds(25200), start.plusSeconds(28800), status = 2)
            row(start.plusSeconds(32400), start.plusSeconds(36000), self = 2)
            var calls = 0
            val provider = AndroidBusyProvider(context, { zone }, { BusyPermission.Granted }) { uri, projection, selection, _ ->
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                assertEquals(setOf("begin", "end", "allDay", "availability", "eventStatus", "selfAttendeeStatus"), projection.toSet())
                val bounds = uri.pathSegments.takeLast(2).map(String::toLong)
                if (calls++ == 0) assertEquals(window.start.toEpochMilli(), bounds[0])
                else assertEquals(Instant.parse("2026-09-15T00:00:00Z").toEpochMilli(), bounds[0])
                db.query("instances", projection, selection, null, null, null, "begin ASC")
            }
            val result = provider.read(BusyQuery(window, window))
            assertEquals(2, calls); assertEquals(2, result.size)
            assertEquals(start.plusSeconds(3600), result[0].start)
            row(Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"), all = 1)
            calls = 0
            assertEquals(listOf(window), provider.read(BusyQuery(window, window)))
        }
    }
    @Test fun deniedNeverQueriesAndNullCursorIsFailure() = runBlocking {
        var calls = 0
        val denied = AndroidBusyProvider(context, permissionCheck = { BusyPermission.NotGranted }, queryCursor = { _, _, _, _ -> calls++; null })
        assertTrue(runCatching { denied.read(BusyQuery(window, window)) }.exceptionOrNull() is SecurityException)
        assertEquals(0, calls)
        val failed = AndroidBusyProvider(context, permissionCheck = { BusyPermission.Granted }, queryCursor = { _, _, _, _ -> null })
        assertTrue(runCatching { failed.read(BusyQuery(window, window)) }.isFailure)
    }
    @Test fun cancellationReachesBlockingQuerySignal() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val provider = AndroidBusyProvider(context, permissionCheck = { BusyPermission.Granted }, queryCursor = { _, _, _, signal ->
            signal.setOnCancelListener { cancelled.complete(Unit) }
            entered.complete(Unit)
            runBlocking { cancelled.await() }
            signal.throwIfCanceled(); null
        })
        val job = launch { provider.read(BusyQuery(window, window)) }
        withTimeout(5000) { entered.await() }; job.cancel()
        withTimeout(5000) { cancelled.await(); job.join() }
    }
}
