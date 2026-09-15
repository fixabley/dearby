package io.fixabley.dearby.app

import androidx.compose.runtime.*
import io.fixabley.dearby.features.calendarbusy.api.BusyPermission
import io.fixabley.dearby.features.calendarbusy.api.BusyProvider

internal enum class CalendarSettingsStatus { Off, Active, Waiting, Denied, Restricted, Revoked, Failed }

/** App-owned preference/permission coordinator; detail BusySessions still own ephemeral intervals. */
internal class CalendarSettingsController(private val provider: BusyProvider, private val store: CalendarSettingsStore) {
    var enabled by mutableStateOf(store.enabled); private set
    var firstPromptHandled by mutableStateOf(store.firstPromptHandled); private set
    var status by mutableStateOf(if (enabled) CalendarSettingsStatus.Active else CalendarSettingsStatus.Off); private set
    private var generation = 0L
    private var pending: Long? = null
    init { resume() }
    private fun save() = store.write(enabled, firstPromptHandled)
    fun later() { firstPromptHandled = true; disable() }
    fun enable(): Long? {
        firstPromptHandled = true
        generation++
        pending = null
        enabled = false
        when (provider.permission()) {
            BusyPermission.Granted -> { enabled = true; status = CalendarSettingsStatus.Active }
            BusyPermission.Restricted -> status = CalendarSettingsStatus.Restricted
            BusyPermission.NotGranted -> { status = CalendarSettingsStatus.Waiting; pending = generation }
        }
        save()
        return pending
    }
    fun permissionResult(token: Long) {
        if (pending != token || generation != token) return
        pending = null
        when (provider.permission()) {
            BusyPermission.Granted -> { enabled = true; status = CalendarSettingsStatus.Active }
            BusyPermission.NotGranted -> { enabled = false; status = CalendarSettingsStatus.Denied }
            BusyPermission.Restricted -> { enabled = false; status = CalendarSettingsStatus.Restricted }
        }
        save()
    }
    fun failed(token: Long) {
        if (pending != token) return
        disable(); status = CalendarSettingsStatus.Failed
    }
    fun disable() {
        generation++; pending = null; enabled = false; status = CalendarSettingsStatus.Off; save()
    }
    fun background() { if (pending != null) disable() }
    fun resume() {
        if (enabled && provider.permission() != BusyPermission.Granted) {
            disable()
            status = if (provider.permission() == BusyPermission.Restricted) CalendarSettingsStatus.Restricted else CalendarSettingsStatus.Revoked
        }
    }
}
