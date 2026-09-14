package io.fixabley.dearby.features.calendarbusy

import io.fixabley.dearby.features.calendarbusy.api.*
import io.fixabley.dearby.shared.ui.*
import java.time.Instant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BusySessionTest {
    private val window = BusyInterval(Instant.parse("2026-09-15T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"))
    private val query = BusyQuery(window, window)
    private class Fake : BusyProvider {
        var access = BusyPermission.NotGranted
        var reads = 0
        var action: suspend () -> List<BusyInterval> = { emptyList() }
        override fun permission() = access
        override suspend fun read(query: BusyQuery): List<BusyInterval> { reads++; return action() }
    }
    @Test fun consentCancelDenialAndGrantedSwitch() = runBlocking {
        val fake = Fake(); val session = BusySession(fake, this)
        session.select(0, query); session.enable()
        assertEquals(BusyConnection.Consent, session.connection); assertEquals(0, fake.reads)
        session.off(); assertFalse(session.enabled)
        session.enable(); session.permissionResult(session.confirm()!!)
        assertEquals(BusyConnection.Denied, session.connection); assertTrue(session.results.isEmpty())
        fake.access = BusyPermission.Granted; session.enable(); yield()
        assertEquals(BusyLoad.Ready, session.results[0]?.load)
        assertEquals(emptyList<BusyInterval>(), session.results[0]?.intervals)
        session.off(); assertTrue(session.results.isEmpty())
    }
    @Test fun offAndNewDateFenceNonCooperativeResults() = runBlocking {
        val fake = Fake().apply { access = BusyPermission.Granted }
        val pending = CompletableDeferred<List<BusyInterval>>()
        fake.action = { withContext(NonCancellable) { pending.await() } }
        val session = BusySession(fake, this)
        session.select(0, query); session.enable(); yield()
        assertEquals(BusyLoad.Loading, session.results[0]?.load)
        session.off(); pending.complete(listOf(window)); yield(); yield()
        assertTrue(session.results.isEmpty())
        val older = CompletableDeferred<List<BusyInterval>>()
        fake.action = { withContext(NonCancellable) { older.await() } }
        session.enable(); yield()
        fake.action = { emptyList() }
        session.select(0, BusyQuery(BusyInterval(window.end, window.end.plusSeconds(86400)), window)); yield()
        older.complete(listOf(window)); yield(); yield()
        assertEquals(emptyList<BusyInterval>(), session.results[0]?.intervals)
        session.close()
    }
    @Test fun failureRevocationResumeAndCloseClearMemory() = runBlocking {
        val fake = Fake().apply { access = BusyPermission.Granted; action = { error("test") } }
        val session = BusySession(fake, this); session.select(0, query); session.enable(); yield()
        assertEquals(BusyLoad.Failed, session.results[0]?.load)
        fake.action = { listOf(window) }; session.retry(); yield()
        assertEquals(listOf(window), session.results[0]?.intervals)
        session.background(); assertTrue(session.results.isEmpty())
        fake.access = BusyPermission.NotGranted; session.resume()
        assertEquals(BusyConnection.Revoked, session.connection)
        session.close(); fake.access = BusyPermission.Granted; session.enable(); yield()
        assertFalse(session.enabled); assertTrue(session.results.isEmpty())
    }
    @Test fun restrictedAndLatePermissionCannotEnable() = runBlocking {
        val fake = Fake(); val session = BusySession(fake, this)
        session.enable(); val token = session.confirm()!!; session.off()
        fake.access = BusyPermission.Granted; session.permissionResult(token)
        assertFalse(session.enabled)
        fake.access = BusyPermission.Restricted; session.enable()
        assertEquals(BusyConnection.Restricted, session.connection)
    }
}
