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

    @Test fun savedAndGuestProfileAndMemoryEdit() {
        compose.onNodeWithContentDescription("Dearby 개발자 컨퍼런스 저장").performClick()
        tab("저장")
        compose.onNodeWithText("Dearby 개발자 컨퍼런스").assertIsDisplayed()
        screenshot("selected-saved")
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

    @Test fun qrShowShareNewCardVisibilityAndPreview() {
        tab("QR")
        compose.onNodeWithContentDescription("명함 QR, 누르면 확대").assertIsDisplayed()
        compose.onNodeWithText("새 명함", substring = true).assertIsDisplayed()
        screenshot("selected-qr-show")
        compose.onNodeWithContentDescription("선택 명함 편집").performClick()
        compose.onNodeWithText("명함 이름").performScrollTo().performTextReplacement("수정한 네트워킹")
        compose.onNodeWithText("명함 수정 저장").performClick()
        compose.onAllNodesWithText("수정한 네트워킹").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("명함 QR, 누르면 확대").performClick()
        compose.onNodeWithContentDescription("확대된 예시 QR").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithContentDescription("공유 메뉴").performClick()
        screenshot("selected-qr-share-menu")
        compose.onNodeWithText("QR 이미지 저장").performClick()
        compose.onNodeWithText("확인").performClick()
        compose.onNodeWithText("내 명함").performScrollTo()
        compose.onNodeWithText("새 명함", substring = true).performClick()
        compose.onNodeWithText("명함 만들기").performScrollTo()
        screenshot("selected-qr-new-card")
        compose.onNodeWithText("명함 만들기").performClick()
        screenshot("selected-card-editor")
        compose.onNodeWithText("전화").performClick()
        compose.onNodeWithText("미리보기").performClick()
        compose.onNodeWithText("미리보기 닫기").assertIsDisplayed().performClick()
        compose.onNodeWithText("공유 카드 만들기").performClick()
        compose.onNodeWithContentDescription("명함 QR, 누르면 확대").assertIsDisplayed()
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
