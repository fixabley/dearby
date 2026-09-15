package io.fixabley.dearby

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.fixabley.dearby.shared.ui.*
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DayTimelineTest {
    @get:Rule val rule = createComposeRule()
    private fun range(s: String, e: String) = TimelineInterval(OffsetDateTime.parse(s).toInstant(),
        OffsetDateTime.parse(e).toInstant(), ZoneId.of("Asia/Seoul"))

    @Test fun nativePickerAndAdjacentControlsChangeOnlySelectedDay() {
        val interval = range("2026-08-27T09:00:00+09:00", "2026-09-15T13:00:00+09:00")
        rule.setContent { DearbyTheme { Column(Modifier.safeDrawingPadding()) { DayTimeline(interval, "신청") } } }
        rule.onNodeWithTag("timeline.previous").assertIsNotEnabled()
        rule.onNodeWithTag("timeline.date").performClick()
        rule.onNodeWithText("Friday, August 28, 2026").performClick()
        rule.onNodeWithText("선택").performClick()
        rule.onNodeWithTag("timeline.date").assertTextContains("2026년 8월 28일 (금)")
        rule.onNodeWithTag("timeline.block").assertContentDescriptionContains("오전 12시부터 다음 날 오전 12시까지", substring = true)
        rule.onNodeWithTag("timeline.previous").performClick()
        rule.onNodeWithTag("timeline.date").assertTextContains("2026년 8월 27일 (목)")
        rule.onNodeWithTag("timeline.next").performClick()
        rule.onNodeWithTag("timeline.date").assertTextContains("2026년 8월 28일 (금)")
    }
    @Test fun shortBlockAtTwoTimesFontRetainsPreciseAccessibleTime() {
        val interval = range("2026-09-15T23:59:58+09:00", "2026-09-15T23:59:59.5+09:00")
        rule.setContent { DearbyTheme(darkTheme = true) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { Column(Modifier.safeDrawingPadding()) { DayTimeline(interval, "짧은 일정") } }
        } }
        rule.onNodeWithTag("timeline.next").assertIsNotEnabled()
        rule.onNodeWithTag("timeline.previous").assertIsNotEnabled()
        rule.onNodeWithTag("timeline.block").assertContentDescriptionContains("59.5초", substring = true)
            .assertContentDescriptionContains("확대 표시", substring = true)
        rule.onNodeWithText("짧은 구간은 블록을 확대해 표시합니다. 정확한 시간은 위 안내를 확인해 주세요.").assertExists()
    }
    @Test fun linkCardInvokesOnlySuppliedCallbackWithFullDestinationSemantics() {
        var opened = 0
        val url = "https://example.com/room?number=501"
        rule.setContent { DearbyTheme { Column(Modifier.safeDrawingPadding()) { LinkCard("example.com", url, { opened++ }) } } }
        rule.onNodeWithContentDescription(url).assertExists()
        rule.runOnIdle { assertEquals(0, opened) }
        rule.onNodeWithContentDescription("example.com 링크 열기").performClick()
        rule.runOnIdle { assertEquals(1, opened) }
    }
}
