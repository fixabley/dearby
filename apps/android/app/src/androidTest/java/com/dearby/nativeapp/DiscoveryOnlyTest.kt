package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class DiscoveryOnlyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onlyDiscoveryDetailCalendarAndApplicationAreReachable() {
        compose.onNodeWithText("모집 중인 활동").assertExists()
        listOf("저장", "QR", "받은 명함", "내 프로필").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("검증 전용 활동 · 실제 모집 아님").fetchSemanticsNodes().isNotEmpty() }
        capture("discovery")
        compose.onNodeWithText("검증 전용 활동 · 실제 모집 아님").performClick()
        compose.onNodeWithContentDescription("프로그램 저장").assertDoesNotExist()
        compose.onNodeWithText("조직 저장").assertDoesNotExist()
        compose.onNodeWithText("겹치는 시간 확인하기").performScrollTo().performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("닫기").fetchSemanticsNodes().isNotEmpty() }
        capture("calendar")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("신청 사이트 열기").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("신청하셨나요?").assertExists()
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithText("이 활동은 이미 신청한 활동이에요.").assertDoesNotExist()
        capture("detail")
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "discovery-$name.png")
        (if (name == "calendar") compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().let { image -> file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}
