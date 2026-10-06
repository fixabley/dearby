package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test

class PrototypeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) = capturePrototype(compose, name)

    @Test fun discoveryApplicationAndMemoryReport() {
        compose.onNodeWithText("활동 둘러보기").assertIsDisplayed()
        compose.onNodeWithText("예시").assertIsDisplayed()
        screenshot("prototype-discovery")
        compose.onNodeWithText("선발형", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Dearby 메이커 캠프").assertIsDisplayed().performClick()
        screenshot("prototype-detail")
        compose.onNodeWithText("공식 사이트에서 신청").performClick()
        compose.onNodeWithText("신청 흐름 살펴보기").assertIsDisplayed()
        screenshot("prototype-application")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("신청함").performClick()
        compose.onNodeWithText("예시 신청 상태를 기록했어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertDoesNotExist()
        compose.onNodeWithText("전체", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertIsDisplayed()
        compose.onNodeWithText("내 활동").performClick()
        compose.onAllNodesWithText("신청함").assertCountEquals(2)
        compose.onNodeWithText("Dearby 메이커 캠프").assertIsDisplayed()
    }
    @Test fun myActivitiesConfirmMarkAndEmptyState() {
        compose.onNodeWithText("내 활동").performClick()
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertIsDisplayed()
        compose.onNodeWithText("신청함").assertIsDisplayed()
        compose.onNodeWithText("참여 확정 표시").assertIsOff().performClick()
        compose.onNodeWithText("참여 확정").assertIsDisplayed()
        screenshot("my-activities")
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").performClick()
        compose.onNodeWithText("참여 확정 표시").assertIsOn().performClick()
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        compose.onNodeWithText("신청 안 함").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("신청한 활동이 없어요. 발견에서 관심 있는 활동을 신청해 보세요.").assertIsDisplayed()
        compose.onNodeWithText("활동 둘러보기").performClick()
        compose.onNodeWithText("Dearby 메이커 캠프").assertIsDisplayed()
    }
    @Test fun catalogReferenceScreens() {
        // The conference starts applied, so capture that state before clearing it for the reference screens.
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").performClick()
        screenshot("activity-applied")
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        compose.onNodeWithText("신청 안 함").performClick()
        compose.onNodeWithText("예시 활동").performScrollTo()
        screenshot("activity-detail-top")
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo()
        screenshot("activity-detail-schedule")
        compose.onNodeWithText("신청 상태 수정").performScrollTo()
        screenshot("activity-detail-bottom")
        compose.onNodeWithText("공식 사이트에서 신청").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("신청함").performClick()
        compose.onNodeWithText("예시 신청 상태를 기록했어요.").assertIsDisplayed()
    }
    @Test fun conferenceConflictAndMeetupZeroConflict() {
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("60분이 겹쳐요").assertIsDisplayed()
        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        compose.onNodeWithText("선택한 캘린더로 확인").assertDoesNotExist()
        screenshot("prototype-overlap")
        compose.onNodeWithText("확인했어요").performClick()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("Dearby 커뮤니티 밋업").performScrollTo().performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("겹치는 시간이 없어요").assertIsDisplayed()
        screenshot("prototype-no-overlap")
    }
}
