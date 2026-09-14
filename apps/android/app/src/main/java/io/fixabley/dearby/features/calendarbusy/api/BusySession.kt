package io.fixabley.dearby.features.calendarbusy.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.fixabley.dearby.shared.ui.BusyInterval
import io.fixabley.dearby.shared.ui.mergedBusy
import io.fixabley.dearby.shared.ui.busyOverlaps
import kotlinx.coroutines.*

internal enum class BusyConnection { Off, Consent, Requesting, Denied, Restricted, Revoked, Active }
internal enum class BusyLoad { Loading, Failed, Ready }
internal data class BusyResult(val load: BusyLoad, val intervals: List<BusyInterval> = emptyList(), val overlaps: Boolean = false)

/** Main-thread, detail-session owner. No persistent state, event metadata, or application periods. */
internal class BusySession(private val provider: BusyProvider, private val scope: CoroutineScope) {
    var connection by mutableStateOf(BusyConnection.Off); private set
    var results by mutableStateOf<Map<Int, BusyResult>>(emptyMap()); private set
    private val queries = mutableMapOf<Int, BusyQuery>()
    private val jobs = mutableMapOf<Int, Job>()
    private val revisions = mutableMapOf<Int, Long>()
    private var generation = 0L
    private var closed = false
    private var foreground = true
    val enabled get() = connection == BusyConnection.Active

    fun enable() {
        if (closed) return
        when (provider.permission()) {
            BusyPermission.Granted -> { connection = BusyConnection.Active; reload() }
            BusyPermission.NotGranted -> connection = BusyConnection.Consent
            BusyPermission.Restricted -> connection = BusyConnection.Restricted
        }
    }
    fun confirm(): Long? {
        if (connection != BusyConnection.Consent || closed) return null
        connection = BusyConnection.Requesting
        return generation
    }
    fun permissionResult(token: Long) {
        if (closed || token != generation || connection != BusyConnection.Requesting) return
        connection = when (provider.permission()) {
            BusyPermission.Granted -> BusyConnection.Active
            BusyPermission.NotGranted -> BusyConnection.Denied
            BusyPermission.Restricted -> BusyConnection.Restricted
        }
        if (enabled) reload()
    }
    fun off() { clear(); connection = BusyConnection.Off }
    fun close() { off(); queries.clear(); closed = true }
    fun background() { foreground = false; clear() }
    fun resume() {
        foreground = true
        if (!enabled || closed) return
        if (provider.permission() != BusyPermission.Granted) revoke() else reload()
    }
    fun select(key: Int, query: BusyQuery) {
        if (closed || queries[key] == query) return
        queries[key] = query
        load(key, query)
    }
    fun retry() { if (enabled) reload() }
    private fun revoke() { clear(); connection = BusyConnection.Revoked }
    private fun clear() {
        generation++
        jobs.values.forEach { it.cancel() }; jobs.clear()
        results = emptyMap()
    }
    private fun reload() { clear(); queries.toMap().forEach { (key, query) -> load(key, query) } }
    private fun load(key: Int, query: BusyQuery) {
        jobs.remove(key)?.cancel()
        val revision = (revisions[key] ?: 0) + 1
        revisions[key] = revision
        results = results - key
        if (!enabled || !foreground || closed) return
        val token = generation
        results = results + (key to BusyResult(BusyLoad.Loading))
        jobs[key] = scope.launch {
            fun current() = !closed && foreground && enabled && generation == token && revisions[key] == revision
            try {
                val values = provider.read(query)
                if (current()) {
                    if (provider.permission() != BusyPermission.Granted) revoke()
                    else results = results + (key to BusyResult(BusyLoad.Ready, mergedBusy(values, query.window), busyOverlaps(values, query.activity)))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { if (current()) revoke() }
            catch (_: Exception) { if (current()) results = results + (key to BusyResult(BusyLoad.Failed)) }
        }
    }
}
