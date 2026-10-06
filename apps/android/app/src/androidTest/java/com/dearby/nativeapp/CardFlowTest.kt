package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test

class CardFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) = capturePrototype(compose, name)
    private fun tab(label: String) = compose.onAllNodesWithText(label).onLast().performClick()

    @Test fun guestProfileAndMemoryEdit() {
        tab("내 프로필")
        compose.onNodeWithText("로그인하고 시작하기").assertIsDisplayed()
        screenshot("selected-profile-guest")
        compose.onNodeWithText("로그인하고 시작하기").performClick()
        compose.onNodeWithText("김지민").assertIsDisplayed()
        screenshot("selected-profile")
        compose.onNodeWithText("편집").performClick()
        compose.onNodeWithText("이름").performTextReplacement("김지민 예시")
        compose.onNodeWithText("프로필 저장").performScrollTo().performClick()
        compose.onNodeWithText("김지민 예시").performScrollTo().assertIsDisplayed()
    }

    @Test fun qrSignedOutOffersCardCreation() {
        tab("QR")
        compose.onNodeWithText("명함 만들기").assertIsDisplayed()
        compose.onNodeWithContentDescription("명함 QR").assertDoesNotExist()
        screenshot("selected-qr-signed-out")
        compose.onNodeWithText("명함 만들기").performClick()
        compose.onNodeWithText("로그인하고 명함 발행").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("명함 만들기").assertIsDisplayed()
    }

    @Test fun exampleScanPublicCardSaveAndSend() {
        tab("QR")
        compose.onNodeWithText("QR 찍기").performClick()
        screenshot("selected-qr-scan")
        compose.onNodeWithText("사진에서 선택").performScrollTo().performClick()
        compose.onNodeWithText("공유 카드").assertIsDisplayed()
        screenshot("selected-shared-card")
        compose.onNodeWithText("카드 저장").performClick()
        compose.onNodeWithText("카드 저장됨").assertIsDisplayed()
        compose.onNodeWithText("나도 카드 주기").performClick()
        compose.onNodeWithText("내 명함 선택").assertIsDisplayed()
        screenshot("selected-send-card-picker")
        compose.onNodeWithText("이 명함 보내기").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("내 명함을 주지 않은 상대 3").assertIsDisplayed()
    }

    @Test fun sendingLastWalletCardKeepsRemainingGroupUsable() {
        tab("받은 명함")
        repeat(2) { compose.onNodeWithContentDescription("다음 명함").performClick(); compose.waitForIdle() }
        compose.onNodeWithText("나도 명함 주기").performClick()
        compose.onNodeWithText("이 명함 보내기").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("내 명함을 주지 않은 상대 2").assertIsDisplayed()
        compose.onNodeWithText("명함 상세보기").performClick()
        compose.onNodeWithText("공유 카드").assertIsDisplayed()
    }

    @Test fun walletSearchDetailsAndReciprocalOnlyGroup() {
        tab("받은 명함")
        screenshot("selected-wallet")
        listOf("최유진", "박서연", "이도윤").forEachIndexed { index, name ->
            compose.onNode(hasSetTextAction()).performTextReplacement(name)
            if (index == 0) {
                compose.onNodeWithText("명함 상세보기").performClick()
                compose.onNodeWithText("공유 카드").assertIsDisplayed()
                compose.onNodeWithContentDescription("뒤로").performClick()
            }
            compose.onNodeWithText("나도 명함 주기").performClick()
            compose.onNodeWithText("이 명함 보내기").performClick()
            compose.onNodeWithText("확인").performClick()
        }
        compose.onNodeWithText("나도 명함 주기").assertDoesNotExist()
        screenshot("selected-wallet-reciprocal-only")
    }
}
