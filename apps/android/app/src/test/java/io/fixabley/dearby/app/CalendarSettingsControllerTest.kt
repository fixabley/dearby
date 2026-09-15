package io.fixabley.dearby.app

import io.fixabley.dearby.features.calendarbusy.api.*
import io.fixabley.dearby.shared.ui.BusyInterval
import org.junit.Assert.*
import org.junit.Test

class CalendarSettingsControllerTest {
    private class Store : CalendarSettingsStore {
        override var enabled = false
        override var firstPromptHandled = false
        override fun write(enabled: Boolean, firstPromptHandled: Boolean) { this.enabled = enabled; this.firstPromptHandled = firstPromptHandled }
    }
    private class Provider : BusyProvider {
        var access = BusyPermission.NotGranted
        override fun permission() = access
        override suspend fun read(query: BusyQuery): List<BusyInterval> = error("Settings must never query intervals")
    }
    @Test fun laterPersistsHandledWithoutPermissionOrQuery() {
        val store = Store(); val provider = Provider(); val settings = CalendarSettingsController(provider, store)
        assertFalse(settings.firstPromptHandled); settings.later()
        val restarted = CalendarSettingsController(provider, store)
        assertTrue(restarted.firstPromptHandled); assertFalse(restarted.enabled)
        assertEquals(CalendarSettingsStatus.Off, restarted.status)
    }
    @Test fun explicitEnablePersistsOnlyAfterGrantAndRevocationDisables() {
        val store = Store(); val provider = Provider(); val settings = CalendarSettingsController(provider, store)
        val token = settings.enable()!!
        assertFalse(store.enabled); assertTrue(store.firstPromptHandled)
        provider.access = BusyPermission.Granted; settings.permissionResult(token)
        assertTrue(CalendarSettingsController(provider, store).enabled)
        provider.access = BusyPermission.NotGranted; settings.resume()
        assertFalse(store.enabled); assertEquals(CalendarSettingsStatus.Revoked, settings.status)
    }
    @Test fun denialAndFailureRemainOffAndGrantedSettingsReusePermission() {
        val store = Store(); val provider = Provider(); val settings = CalendarSettingsController(provider, store)
        settings.permissionResult(settings.enable()!!)
        assertEquals(CalendarSettingsStatus.Denied, settings.status); assertFalse(store.enabled)
        settings.failed(settings.enable()!!)
        assertEquals(CalendarSettingsStatus.Failed, settings.status); assertFalse(store.enabled)
        provider.access = BusyPermission.Granted
        assertNull(settings.enable()); assertTrue(store.enabled)
        settings.disable(); assertFalse(store.enabled)
    }
    @Test fun offAndBackgroundFenceDelayedPermissionAndRestrictedStaysOff() {
        val store = Store(); val provider = Provider(); val settings = CalendarSettingsController(provider, store)
        val old = settings.enable()!!; settings.disable(); provider.access = BusyPermission.Granted; settings.permissionResult(old)
        assertFalse(settings.enabled)
        provider.access = BusyPermission.NotGranted
        val stopped = settings.enable()!!; settings.background(); provider.access = BusyPermission.Granted; settings.permissionResult(stopped)
        assertFalse(settings.enabled)
        provider.access = BusyPermission.Restricted; assertNull(settings.enable())
        assertEquals(CalendarSettingsStatus.Restricted, settings.status)
    }
}
