package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test

class LargeTextFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun scrollingKeepsCardActionsReachable() {
        compose.onNodeWithText("QR").performClick()
        compose.onNodeWithText("내 명함").performScrollTo()
        compose.onNodeWithText("새 명함", substring = true).assertIsDisplayed()
        capturePrototype(compose, "large-text-qr")
        compose.onNodeWithText("새 명함", substring = true).performClick()
        compose.onNodeWithText("명함 만들기").performScrollTo().performClick()
        compose.onNodeWithContentDescription("캠퍼스 해커톤 포함").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("공유 카드 만들기").assertIsDisplayed()
        compose.onNodeWithText("예시 명함 · 선택한 내용은 이번 실행 중에만 유지돼요.").performScrollTo()
        capturePrototype(compose, "large-text-card-editor")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("받은 명함").performClick()
        compose.onNodeWithText("명함 상세보기").assertIsDisplayed()
        compose.onNodeWithText("나도 명함 주기").assertIsDisplayed()
        capturePrototype(compose, "large-text-wallet")
        compose.onNodeWithText("나도 명함 주기").performClick()
        compose.onNodeWithText("이 명함 보내기").assertIsDisplayed()
        capturePrototype(compose, "large-text-send")
    }
}
