package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Full activity + system bars. Requires the isolated server account prepared by LocalApiIntegrationTest. */
class FullAppVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "app-$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun authenticatedTabsAndCardPreview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localApi") == "true")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("새로고침").fetchSemanticsNodes().isNotEmpty() }
        capture("discovery")
        compose.onNodeWithText("내 프로필", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Android 검증 계정").fetchSemanticsNodes().isNotEmpty() }
        capture("profile")
        compose.onNodeWithText("QR", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("명함 QR, 누르면 확대").assertIsDisplayed()
        capture("qr")
        compose.onNodeWithText("QR 찍기").performClick(); capture("scan")
        val cardId = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "integration-public-ids").readLines()[1]
        compose.onNodeWithText("명함 링크 붙여넣기").performScrollTo().performTextInput("dearby://card/$cardId")
        compose.onNodeWithText("명함 확인").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("공유 카드").fetchSemanticsNodes().isNotEmpty() }
        capture("shared")
        compose.onNodeWithText("카드 저장").performClick()
        compose.onNodeWithText("이 기기에 저장").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("받은 명함").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("이 기기에 명함을 저장했습니다.").fetchSemanticsNodes().isEmpty() }
        capture("wallet")
        compose.onNodeWithText("QR", useUnmergedTree = true).performClick()
        compose.onNodeWithText("＋\n새 명함").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("새 명함 만들기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("새 명함 만들기").performScrollTo().performClick(); capture("editor")
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("내 명함").assertExists()
    }

    @Test fun sharedCardLoginReturnsToPickerWithoutSending() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("loginReturn") == "true")
        val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        compose.waitUntil(30_000) { compose.onAllNodesWithText("새로고침").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("내 프로필", useUnmergedTree = true).performClick()
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.dearby.nativeapp.app.DearbyViewModel::class.java]
        compose.waitUntil(30_000) { !model.state.value.busy }
        compose.onNodeWithText("로그아웃").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(30_000) { !model.state.value.loggedIn || model.state.value.message != null }
        org.junit.Assert.assertFalse("Logout failed: ${model.state.value.message}", model.state.value.loggedIn)
        compose.onNodeWithText("이메일로 로그인").assertExists()
        compose.onNodeWithText("QR", useUnmergedTree = true).performClick()
        compose.onNodeWithText("QR 찍기").performClick()
        val id = File(files, "integration-public-ids").readLines()[1]
        compose.onNodeWithText("명함 링크 붙여넣기").performScrollTo().performTextInput("dearby://card/$id")
        compose.onNodeWithText("명함 확인").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("공유 카드").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("카드 저장").performClick()
        compose.onNodeWithText("이 기기에 명함을 저장해요. 로그인하지 않고 저장한 명함은 앱을 삭제하면 복구할 수 없어요.").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("나도 카드 주기").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("공유 카드").assertIsDisplayed()
        compose.onNodeWithText("나도 카드 주기").performClick()
        compose.onNodeWithText("이메일").performTextInput("android@example.test")
        compose.onNodeWithText("인증번호 받기").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("이메일 인증번호").fetchSemanticsNodes().isNotEmpty() }
        File(files, "visual-login-waiting").writeText("ready")
        val otp = File(files, "visual-login-otp")
        compose.waitUntil(60_000) { otp.exists() }
        compose.onNodeWithText("이메일 인증번호").performTextInput(otp.readText().trim())
        otp.delete(); File(files, "visual-login-waiting").delete()
        compose.onNodeWithText("로그인").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("내 명함 선택").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("이 명함 보내기").assertIsDisplayed()
        compose.onNodeWithText("서버가 명함 전달을 확인했습니다. 열람 여부는 알 수 없습니다.").assertDoesNotExist()
        capture("login-return-picker")
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("공유 카드").assertIsDisplayed()
    }
}
