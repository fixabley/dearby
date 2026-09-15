package io.fixabley.dearby

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.*
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.features.calendarbusy.api.*
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.pages.noticedetail.model.NoticeScheduleState
import io.fixabley.dearby.shared.ui.BusyInterval
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BusyCalendarFlowTest {
    @get:Rule val rule = createComposeRule()
    private class Fake : BusyProvider {
        var access = BusyPermission.NotGranted
        var pending: CompletableDeferred<List<BusyInterval>>? = null
        val queries = mutableListOf<BusyQuery>()
        override fun permission() = access
        override suspend fun read(query: BusyQuery): List<BusyInterval> {
            queries.add(query)
            pending?.let { return withContext(NonCancellable) { it.await() } }
            return listOf(BusyInterval(query.activity.start, minOf(query.activity.end, query.activity.start.plusSeconds(9000))))
        }
    }
    private fun show(fake: Fake, dark: Boolean = false, large: Boolean = false, initiallyEnabled: Boolean = true): CalendarSettingsController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = NoticeSession(AssetNoticeSnapshotReader(context.assets), FavoritesState(object : FavoriteStore {
            override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {}
        }))
        runBlocking { catalog.load() }
        val notice = catalog.detail("cieat-NCR000000007344").state!!.copy(
            schedules = listOf(NoticeScheduleState(NoticePhase("event", startsAt = "2026-09-15T09:00:00+09:00",
                endsAt = "2026-09-16T13:00:00+09:00", timezone = "Asia/Seoul"), emptyList())))
        val controller = CalendarSettingsController(fake, object : CalendarSettingsStore {
            override var enabled = initiallyEnabled
            override var firstPromptHandled = true
            override fun write(enabled: Boolean, firstPromptHandled: Boolean) { this.enabled = enabled; this.firstPromptHandled = firstPromptHandled }
        })
        rule.setContent {
            DearbyTheme(darkTheme = dark, dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                    NoticeDetailRoute(notice, fake, {}, {}, {}, {}, enabled = controller.enabled)
                }
            }
        }
        return controller
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "busy-evidence").apply { mkdirs() }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300) // Wait for the window compositor after Compose settles.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun settingsOffCancelsPendingDetailAndRejectsLateResult() {
        val fake = Fake().apply { access = BusyPermission.Granted; pending = CompletableDeferred() }
        val controller = show(fake, initiallyEnabled = false)
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("schedule.title.0"))
        rule.onNodeWithTag("schedule.timeline.0").performScrollTo()
        rule.runOnIdle { assertTrue(fake.queries.isEmpty()); controller.enable() }
        rule.waitUntil(5000) { fake.queries.isNotEmpty() }
        rule.onNodeWithText("선택 날짜의 바쁜 시간을 확인하는 중이에요.").assertExists()
        rule.runOnIdle { controller.disable(); fake.pending!!.complete(listOf(fake.queries.single().activity)) }
        rule.waitForIdle()
        rule.onAllNodesWithTag("busy.block").assertCountEquals(0)
        rule.onAllNodesWithTag("timeline.intersection").assertCountEquals(0)
        rule.onAllNodesWithTag("activity.warning", useUnmergedTree = true).assertCountEquals(0)
        capture("detail-off-after-loading")
    }
    private fun busyFlow(dark: Boolean, large: Boolean, suffix: String) {
        val fake = Fake().apply { access = BusyPermission.Granted }; val controller = show(fake, dark, large)
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("schedule.title.0"))
        rule.onNodeWithTag("schedule.timeline.0").performScrollTo()
        rule.waitUntil(5000) { fake.queries.isNotEmpty() }
        rule.onNodeWithText("선택 날짜 기준 · 활동과 바쁜 시간이 겹쳐요").assertExists()
        capture("busy-$suffix")
        rule.onAllNodesWithTag("timeline.next").onLast().performScrollTo().performClick()
        rule.waitUntil(5000) { fake.queries.size >= 2 }
        rule.onNodeWithTag("busy.block").assertContentDescriptionContains("오전 12시–", substring = true)
        capture("next-day-$suffix")
        rule.runOnIdle {
            assertEquals(2, fake.queries.size)
            assertEquals(fake.queries[0].window.end, fake.queries[1].window.start)
            assertEquals(fake.queries[0].activity.start, fake.queries[0].window.start.plusSeconds(9 * 3600))
        }
        rule.runOnIdle { controller.disable() }
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("schedule.title.0"))
        rule.onNodeWithTag("schedule.timeline.0").performScrollTo()
        rule.onAllNodesWithTag("busy.block").assertCountEquals(0)
        rule.onAllNodesWithTag("timeline.intersection").assertCountEquals(0)
        rule.onAllNodesWithTag("activity.warning", useUnmergedTree = true).assertCountEquals(0)
        capture("hidden-$suffix")
    }
    @Test fun selectedActivityDayOverlapLight() = busyFlow(false, false, "light")
    @Test fun selectedActivityDayOverlapDarkLarge() = busyFlow(true, true, "dark-2x")
}
