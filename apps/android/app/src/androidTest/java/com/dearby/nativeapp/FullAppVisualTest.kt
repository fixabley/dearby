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
}
