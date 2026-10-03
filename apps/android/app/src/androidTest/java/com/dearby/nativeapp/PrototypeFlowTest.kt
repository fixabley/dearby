package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class PrototypeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        compose.waitForIdle()
        File(directory, "$name.png").outputStream().use {
            requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun discoveryApplicationAndMemoryReport() {
        compose.onNodeWithText("활동 둘러보기").assertIsDisplayed()
        compose.onNodeWithText("예시").assertIsDisplayed()
        screenshot("prototype-discovery")
        compose.onNodeWithText("선발형", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Dearby 메이커 캠프").assertIsDisplayed().performClick()
        screenshot("prototype-detail")
        compose.onNodeWithText("신청 사이트 열기").performClick()
        compose.onNodeWithText("신청 흐름 살펴보기").assertIsDisplayed()
        screenshot("prototype-application")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("예시 신청함").performClick()
        compose.onNodeWithText("예시 신청 상태를 기록했어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("목록으로").performClick()
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertDoesNotExist()
        compose.onNodeWithText("전체", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertIsDisplayed()
    }
    @Test fun conferenceConflictAndMeetupZeroConflict() {
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("겹치는 시간 1 / 1").assertIsDisplayed()
        screenshot("prototype-overlap")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithContentDescription("목록으로").performClick()
        compose.onNodeWithText("Dearby 커뮤니티 밋업").performScrollTo().performClick()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.onNodeWithText("선택한 캘린더로 확인").performClick()
        compose.onNodeWithText("선택한 캘린더와 겹치는 시간이 없어요").assertIsDisplayed()
        screenshot("prototype-no-overlap")
    }
}
