package io.fixabley.dearby.shared.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatusPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun loadingAndFailureAnnounceStateWhileRetryRemainsAnIndependentAction() {
        val kind = mutableStateOf(StatusKind.Loading)
        var retries = 0
        rule.setContent {
            DearbyTheme(dynamicColor = false) {
                StatusPanel(if (kind.value == StatusKind.Loading) "불러오는 중" else "불러오기 실패",
                    kind = kind.value, action = if (kind.value == StatusKind.Error) {
                        { PrimaryButton(onClick = { retries++ }) { Text("다시 시도") } }
                    } else null)
            }
        }
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        rule.onNodeWithText("불러오는 중").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.runOnIdle { kind.value = StatusKind.Error }
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        rule.onNodeWithText("불러오기 실패").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText("다시 시도").assertIsEnabled().assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
}
