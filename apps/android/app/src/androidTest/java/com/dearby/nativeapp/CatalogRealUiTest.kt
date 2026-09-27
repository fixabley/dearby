package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Guest product UI with real backend content; no mocked transport or catalog fixture. */
class CatalogRealUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun guestDiscoveryDetailSaveAndExplicitReport() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogUi") == "true")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("모집 중", substring = true).fetchSemanticsNodes().size >= 2 }
        capture("real-catalog-discovery")
        compose.onNodeWithText("if(kakao)", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("선발형 · 신청 후 선정 필요", substring = true).assertExists()
        capture("real-catalog-detail")
        if (compose.onAllNodesWithContentDescription("프로그램 저장").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription("프로그램 저장").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("프로그램 저장 해제").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("조직 저장").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("조직 저장").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("조직 저장 해제").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        compose.onNodeWithText("신청하지 않았어요").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("내 기록: 신청하지 않음").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("신청 사이트 열기").performClick()
        compose.onNodeWithText("외부 브라우저").assertExists()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("신청하셨나요?").assertExists()
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithText("내 기록: 신청하지 않음").assertExists()
        // Explicit self-report only: this is NOT an assertion of actual external submission.
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        compose.onNodeWithText("신청했어요").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("이 활동은 이미 신청한 활동이에요.").fetchSemanticsNodes().isNotEmpty() }
        capture("real-catalog-self-report")
        compose.onNodeWithContentDescription("목록으로").performClick()
        compose.onNodeWithText("저장", useUnmergedTree = true).performClick()
        capture("real-catalog-saved")
        compose.onNodeWithText("QR", useUnmergedTree = true).performClick()
        compose.onNodeWithText("교환한 활동 ·", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("등록 활동").performScrollTo().performClick()
        compose.onNodeWithText("YAPP · 얍 · 28기").performClick()
        compose.onNodeWithText("YAPP · 얍 · 28기").assertExists()
        capture("real-catalog-context")
    }
    @Test fun restoredAfterProcessRestart() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("catalogRestart") == "true")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("모집 중", substring = true).fetchSemanticsNodes().size >= 2 }
        compose.onNodeWithText("if(kakao)", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("이 활동은 이미 신청한 활동이에요.").assertExists()
        compose.onNodeWithContentDescription("프로그램 저장 해제").assertExists()
        compose.onNodeWithText("조직 저장 해제").performScrollTo().assertExists()
        capture("real-catalog-restarted")
    }
}
