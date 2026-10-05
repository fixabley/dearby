package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.shared.ui.DearbyControlsGallery
import com.dearby.nativeapp.shared.ui.DearbyTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 공통 입력 컴포넌트의 접근성 이름·선택 상태를 확인하고 검토용 캡처를 남긴다. */
class SharedControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(editing: Boolean) = compose.setContent { DearbyTheme { Box(Modifier.testTag("gallery")) { DearbyControlsGallery(editing) } } }

    // 시스템 대화상자가 겹쳐도 컴포넌트만 남도록 화면 전체가 아닌 노드를 캡처한다.
    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("gallery").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun controlsExposeNamesAndSelection() {
        show(editing = true)
        compose.onNodeWithText("신청한 활동").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Dearby 개발자 컨퍼런스, 3개").assertExists()
        compose.onNodeWithText("Dearby 메이커 캠프").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("이름, 직무, 활동으로 검색").performTextInput("지민")
        compose.onNodeWithContentDescription("검색어 지우기").performClick()
        compose.onNodeWithContentDescription("검색어 지우기").assertDoesNotExist()
        compose.onNodeWithContentDescription("이름").assertTextEquals("김지민")
        capture("android-controls-edit")
    }

    @Test fun readingStateKeepsLabels() {
        show(editing = false)
        compose.onNodeWithContentDescription("이름, 김지민").assertExists()
        capture("android-controls-read")
    }
}
