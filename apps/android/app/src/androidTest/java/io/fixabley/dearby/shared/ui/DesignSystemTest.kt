package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton
import io.fixabley.dearby.shared.ui.buttons.SecondaryButton
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCard
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DesignSystemTest {
    @get:Rule val rule = createComposeRule()

    @Test fun informationLabelAndValueShareOneAccessibleNode() {
        rule.setContent {
            DearbyTheme(dynamicColor = false) {
                ContentSection { InformationRow("장소", "긴 장소 안내와 주소", Modifier.testTag("fact")) }
            }
        }
        rule.onNodeWithTag("fact").assertTextEquals("장소", "긴 장소 안내와 주소")
        rule.onAllNodes(hasText("장소") and hasText("긴 장소 안내와 주소")).assertCountEquals(1)
    }

    @Test fun nativeButtonsPreserveDisabledContractAndTouchSize() {
        var calls = 0
        rule.setContent {
            DearbyTheme(dynamicColor = false) {
                Column {
                    PrimaryButton({ calls++ }, Modifier.testTag("primary"), enabled = false) { Text("진행") }
                    SecondaryButton({ calls++ }, Modifier.testTag("secondary")) { Text("자세히") }
                }
            }
        }
        rule.onNodeWithTag("primary").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, calls) }
        val secondary = rule.onNodeWithTag("secondary").assertHasClickAction()
        val target = secondary.fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(rule.density) { 48.dp.toPx() }
        assertTrue("Native touch target height", target.height >= minimum)
        assertTrue("Native touch target width", target.width >= minimum)
        secondary.performClick()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun largeTextCardKeepsBodyAboveReachableActions() {
        var saved = 0
        var opened = 0
        val state = NoticeCardState("large", "한국농어촌공사 채용설명회", "채용 › 채용행사 · 충북대학교",
            "참여 대상 안내", "신청 마감 안내", "활동 장소 안내", true, "krc", "한국농어촌공사")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DearbyTheme(darkTheme = true, dynamicColor = false) {
                    Column(Modifier.requiredSize(360.dp, 600.dp)) {
                        NoticeCard(state, "1 / 4", { saved++ }, { opened++ })
                    }
                }
            }
        }
        val body = rule.onNodeWithTag("activity.large").fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithTag("save.large").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val issue = rule.onNodeWithText("확인이 필요한 정보가 있어요").fetchSemanticsNode().boundsInRoot
        assertTrue("Body must not paint behind actions", body.bottom <= save.top)
        assertTrue("Last visible fact must fit above actions", issue.bottom <= save.top)
        rule.onNodeWithTag("save.large").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("details.large").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(1, saved); assertEquals(1, opened) }
    }
}
