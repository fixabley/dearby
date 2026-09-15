package io.fixabley.dearby.features.calendarbusy.api

import io.fixabley.dearby.shared.ui.BusyInterval

internal enum class BusyPermission { Granted, NotGranted, Restricted }
internal data class BusyQuery(val window: BusyInterval, val activity: BusyInterval)
internal interface BusyProvider {
    fun permission(): BusyPermission
    suspend fun read(query: BusyQuery): List<BusyInterval>
}
