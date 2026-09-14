package io.fixabley.dearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.shared.ui.*
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BusyTimelineDisplayTest {
    @get:Rule val rule = createComposeRule()
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(time: String) = ZonedDateTime.parse("2026-09-15T$time+09:00[Asia/Seoul]").toInstant()
    @Test fun onlyPositiveIntersectionWarnsAndActivityRetainsFullWidth() {
        val interval = TimelineInterval(at("09:00:00"), at("10:00:00"), zone)
        val busy = listOf(BusyInterval(at("09:15:00"), at("09:45:00")),
            BusyInterval(at("10:00:00"), at("11:00:00")), BusyInterval(at("11:30:00"), at("12:00:00")))
        rule.setContent { DearbyTheme(dynamicColor = false) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                DayTimeline(interval, "행사", busy = BusyOverlayState("겹침 · 접점 · 비겹침 비교용 가짜 일정", busy))
            }
        } }
        rule.onAllNodesWithTag("busy.block").assertCountEquals(3)
        rule.onAllNodesWithTag("activity.warning", useUnmergedTree = true).assertCountEquals(1)
        val blocks = rule.onAllNodesWithTag("busy.block").fetchSemanticsNodes()
        assertEquals(blocks[0].boundsInRoot.width, blocks[1].boundsInRoot.width, 1f)
        assertTrue(rule.onNodeWithTag("timeline.block").fetchSemanticsNode().boundsInRoot.width > blocks[0].boundsInRoot.width * 2)
        assertEquals(blocks[1].boundsInRoot.width, blocks[2].boundsInRoot.width, 1f)
        rule.onAllNodesWithTag("busy.block")[1].assertContentDescriptionContains("활동과 겹치지 않음", substring = true)
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
        val dir = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "busy-evidence").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(dir, "boundaries-light.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun minimumVisualHeightNeverCreatesFalseWarning() {
        val interval = TimelineInterval(at("09:00:00"), at("09:00:01"), zone)
        rule.setContent { DearbyTheme(darkTheme = true) {
            Column(Modifier.safeDrawingPadding()) {
                DayTimeline(interval, "짧은 활동", busy = BusyOverlayState("접점", listOf(BusyInterval(at("09:00:01"), at("09:00:02")))))
            }
        } }
        rule.onAllNodesWithTag("activity.warning", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("busy.block").assertContentDescriptionContains("활동과 겹치지 않음", substring = true)
    }
    @Test fun dateChangeNeverShowsPreviousWindowResult() {
        val interval = TimelineInterval(at("09:00:00"), at("10:00:00").plusSeconds(86400), zone)
        val window = BusyInterval(at("00:00:00"), at("00:00:00").plusSeconds(86400))
        rule.setContent { DearbyTheme {
            Column(Modifier.safeDrawingPadding()) {
                DayTimeline(interval, "행사", busy = BusyOverlayState("이전 날짜 겹침", listOf(BusyInterval(at("09:00:00"), at("10:00:00"))), window))
            }
        } }
        rule.onNodeWithTag("busy.block").assertExists()
        rule.onNodeWithTag("timeline.next").performClick()
        rule.onNodeWithText("이전 날짜 겹침").assertDoesNotExist()
        rule.onNodeWithText("선택 날짜의 바쁜 시간을 확인하는 중이에요.").assertExists()
        rule.onAllNodesWithTag("busy.block").assertCountEquals(0)
    }

}
