package io.fixabley.dearby

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.*
import io.fixabley.dearby.features.calendarbusy.api.*
import io.fixabley.dearby.shared.ui.BusyInterval
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarSettingsFlowTest {
    @get:Rule val rule = createComposeRule()
    private class Store : CalendarSettingsStore {
        override var enabled = false
        override var firstPromptHandled = false
        override fun write(enabled: Boolean, firstPromptHandled: Boolean) { this.enabled = enabled; this.firstPromptHandled = firstPromptHandled }
    }
    private class Provider : BusyProvider {
        var access = BusyPermission.NotGranted
        override fun permission() = access
        override suspend fun read(query: BusyQuery): List<BusyInterval> = error("No detail query from settings")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
        val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "busy-evidence").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun laterIsRememberedAndSettingsDenialNeverEnables() {
        val store = Store(); val provider = Provider()
        var controller by mutableStateOf(CalendarSettingsController(provider, store))
        var open by mutableStateOf(false); var requests = 0
        rule.setContent { DearbyTheme { CalendarSettingsHost(controller, open, { open = false }, { requests++; it() }) } }
        rule.onNodeWithText("나중에").assertExists(); capture("first-launch")
        rule.onNodeWithText("나중에").performClick()
        rule.runOnIdle { assertEquals(0, requests); controller = CalendarSettingsController(provider, store); open = true }
        rule.onNodeWithText("나중에").assertDoesNotExist()
        rule.onNodeWithTag("settings.calendar.switch").assertIsOff(); capture("settings-off")
        rule.onNodeWithTag("settings.calendar.switch").performClick().assertIsOff()
        rule.runOnIdle { assertEquals(1, requests); assertFalse(store.enabled) }; capture("settings-denied")
    }
    @Test fun grantedEnablePersistsAndSettingsOffNeedsNoPermission() {
        val store = Store(); val provider = Provider().apply { access = BusyPermission.Granted }
        var controller by mutableStateOf(CalendarSettingsController(provider, store))
        var open by mutableStateOf(false); var requests = 0
        rule.setContent { DearbyTheme { CalendarSettingsHost(controller, open, { open = false }, { requests++; it() }) } }
        rule.onNodeWithText("켜기").performClick()
        rule.runOnIdle { assertTrue(store.enabled); assertEquals(0, requests); controller = CalendarSettingsController(provider, store); open = true }
        rule.onNodeWithTag("settings.calendar.switch").assertIsOn(); capture("settings-on")
        rule.onNodeWithTag("settings.calendar.switch").performClick().assertIsOff()
        rule.runOnIdle { assertFalse(store.enabled); assertEquals(0, requests) }
    }
    @Test fun toolbarGearOpensSettingsWithoutQueryOrPermission() {
        val store = Store().apply { firstPromptHandled = true }; val provider = Provider()
        val controller = CalendarSettingsController(provider, store)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = NoticeSession(io.fixabley.dearby.app.data.AssetNoticeSnapshotReader(context.assets),
            io.fixabley.dearby.features.favoriteorganization.model.FavoritesState(object : io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore {
                override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {}
            }))
        rule.setContent { DearbyTheme { DearbyApp(catalog, {}, {}, {}, provider, controller) } }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("catalog.ready").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("discovery.pager").performTouchInput { swipeUp(durationMillis = 100) }
        rule.onNodeWithTag("activity.cieat-NCR000000007306").assertIsDisplayed().performTouchInput { doubleClick() }
        rule.onNodeWithTag("save.cieat-NCR000000007306").assertContentDescriptionContains("저장됨", substring = true)
        val large = android.provider.Settings.System.getFloat(context.contentResolver, android.provider.Settings.System.FONT_SCALE, 1f) > 1.5f
        if (large) capture("feed-page-2x")
        rule.onNodeWithTag("settings.open").performClick()
        rule.onNodeWithText("환경설정").assertExists()
        rule.onNodeWithTag("settings.calendar.switch").assertIsOff()
        capture(if (large) "settings-entry-2x" else "settings-entry")
        rule.onNodeWithText("닫기").performClick()
        rule.onNodeWithTag("details.cieat-NCR000000007306").performClick()
        rule.onNode(hasText("DB손해보험 채용상담회") and hasAnyAncestor(hasTestTag("notice.detail"))).assertExists()
    }

}
