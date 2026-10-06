package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.dearby.nativeapp.app.ApiOrigin
import com.dearby.nativeapp.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Discovery against the test-only catalog fixture served in-process; no example activities in the app. */
class PrototypeFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val server = FixtureServer()
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun screenshot(name: String) = capturePrototype(compose, name)
    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }
    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(label: String) = compose.onAllNodesWithText(label).onLast().performClick()

    @Before fun pointAtFixture() { ApiOrigin.debugOverride = server.origin }
    @After fun reset() { scenario?.close(); server.close(); ApiOrigin.debugOverride = null }

    @Test fun discoveryShowsOpenActivitiesFiltersAndQuickApply() {
        launch()
        waitFor("테스트 컨퍼런스")
        compose.onNodeWithText("테스트 메이커 캠프").assertIsDisplayed()
        compose.onNodeWithText("테스트 예정 밋업").assertDoesNotExist()
        compose.onNodeWithText("테스트 오래된 활동").assertDoesNotExist()
        compose.onNodeWithContentDescription("테스트 컨퍼런스 공식 사이트에서 신청, 외부 브라우저로 열려요").assertExists()
        compose.onNodeWithContentDescription("테스트 메이커 캠프 공식 사이트에서 신청, 외부 브라우저로 열려요").assertDoesNotExist()
        screenshot("catalog-discovery")
        compose.onNodeWithText("선발형", useUnmergedTree = true).performClick()
        compose.onNodeWithText("테스트 컨퍼런스").assertDoesNotExist()
        compose.onNodeWithText("테스트 메이커 캠프").assertIsDisplayed()
    }
    @Test fun failureShowsRetryWithoutExamples() {
        server.failing = true
        launch()
        waitFor("활동을 불러오지 못했어요")
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertDoesNotExist()
        screenshot("catalog-error")
        server.failing = false
        compose.onNodeWithText("다시 시도").performClick()
        waitFor("테스트 컨퍼런스")
    }
    @Test fun detailLinksToOfficialApplicationAndMyActivitiesStartEmpty() {
        launch()
        tab("내 활동")
        waitFor("신청한 활동이 없어요")
        compose.onNodeWithText("활동 둘러보기").performClick()
        waitFor("테스트 컨퍼런스")
        compose.onNodeWithText("테스트 컨퍼런스").performClick()
        compose.onNodeWithText("테스트 주최").assertIsDisplayed()
        compose.onNodeWithContentDescription("공식 사이트에서 신청, 외부 브라우저로 열려요").assertExists()
        screenshot("catalog-detail")
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        compose.onNodeWithText("신청함").performClick()
        compose.onNodeWithText("신청했다고 표시했어요 · 실제 접수 확인이 아니에요").assertIsDisplayed()
        compose.onNodeWithText("참여 확정 표시").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        tab("내 활동")
        compose.onNodeWithText("테스트 컨퍼런스").assertIsDisplayed()
        compose.onNodeWithText("참여 확정").assertIsDisplayed()
        screenshot("my-activities")
    }
    @Test fun conferenceOverlapsAndCampDoesNot() {
        launch()
        waitFor("테스트 컨퍼런스")
        compose.onNodeWithText("테스트 컨퍼런스").performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("60분이 겹쳐요").assertIsDisplayed()
        compose.onNodeWithText("확인했어요").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("테스트 메이커 캠프").performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("겹치는 시간이 없어요").assertIsDisplayed()
    }
}
