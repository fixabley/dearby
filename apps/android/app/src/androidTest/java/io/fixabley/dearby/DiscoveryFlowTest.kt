package io.fixabley.dearby

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class DiscoveryFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun careerDetailSeparatesInterestTargetFromEventSchool() {
        rule.onNodeWithTag("classification.cieat-NCR000000007344", useUnmergedTree = true)
            .assertTextEquals("채용 › 채용행사 · 충북대학교")
        rule.onNodeWithTag("details.cieat-NCR000000007344").performClick()
        rule.onNodeWithText("관심 조직").assertIsDisplayed()
        rule.onNodeWithText("한국농어촌공사").assertIsDisplayed()
        rule.onNodeWithText("행사 관련 기관").assertIsDisplayed()
        rule.onNodeWithText("충북대학교").assertIsDisplayed()
        rule.onNodeWithText("상위 조직").assertDoesNotExist()
    }

    @Test
    fun competitionDetailShowsParentAndEdition() {
        repeat(3) {
            rule.onNodeWithTag("discovery.pager").performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        rule.onNodeWithTag("details.cbnu-software-1154064").performClick()
        rule.onNodeWithText("상위 조직").assertIsDisplayed()
        rule.onNodeWithText("영남권 AI·정보보호영재교육원").assertIsDisplayed()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasText("제2회"))
        rule.onNodeWithText("제2회").assertIsDisplayed()
    }

    @Test
    fun doubleTapSavesDistinctSubjectsAndSurvivesActivityRecreation() {
        // Run on the dedicated test emulator. Start from an empty local favorites store.
        rule.activity.getSharedPreferences("dearby.favorites.v1", Context.MODE_PRIVATE).edit().clear().commit()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("activity.cieat-NCR000000007344")
            .performTouchInput { doubleClick() }
        rule.onNodeWithTag("save.cieat-NCR000000007344")
            .assertTextContains("저장됨", substring = true)
        rule.onNodeWithTag("activity.cieat-NCR000000007344").performTouchInput { doubleClick() }
        rule.onNodeWithTag("discovery.pager").performTouchInput { swipeUp() }
        rule.onNodeWithTag("activity.cieat-NCR000000007306").assertIsDisplayed()
            .performTouchInput { doubleClick() }
        rule.onNodeWithText("즐겨찾기", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("충북대학교 대학일자리센터").assertCountEquals(0)
        rule.onAllNodesWithText("한국농어촌공사").assertCountEquals(1)
        rule.onAllNodesWithText("DB손해보험").assertCountEquals(1)
        rule.onNodeWithText("한국농어촌공사 채용설명회").assertIsDisplayed()
        rule.onNodeWithText("DB손해보험 채용상담회").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onAllNodesWithText("충북대학교 대학일자리센터").assertCountEquals(0)
        rule.onAllNodesWithText("한국농어촌공사").assertCountEquals(1)
        rule.onAllNodesWithText("DB손해보험").assertCountEquals(1)
        rule.onNodeWithTag("remove.krc").performClick()
        rule.onNodeWithText("DB손해보험").assertIsDisplayed()
        rule.onNodeWithTag("remove.db-insurance").performClick()
        rule.onNodeWithText("저장한 조직이 없어요").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("저장한 조직이 없어요").assertIsDisplayed()
    }
}
