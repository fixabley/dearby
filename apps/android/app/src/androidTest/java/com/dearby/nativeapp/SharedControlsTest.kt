package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import com.dearby.nativeapp.shared.ui.DearbyControlsGallery
import com.dearby.nativeapp.shared.ui.DearbySearchField
import com.dearby.nativeapp.widgets.card.cardContent.ReceivedCardGroupsSample
import androidx.compose.ui.semantics.SemanticsProperties
import com.dearby.nativeapp.shared.ui.rememberDearbySearchReveal
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 공통 입력 컴포넌트의 접근성 이름·선택 상태를 확인하고 검토용 캡처를 남긴다. */
class SharedControlsTest {
    @get:Rule val compose = createComposeRule()
    private var query by mutableStateOf("")

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
        compose.onNodeWithContentDescription("함께한 활동, Dearby 개발자 컨퍼런스").assertExists()
        compose.onNodeWithContentDescription("함께한 활동, Dearby 메이커 캠프 여름 시즌 집중 프로그램 외 2개").assertExists()
        capture("android-controls-edit")
    }

    @Test fun collapsedSearchIsButtonThatExpandsAndStaysOpenWhileTyping() {
        var expansion by mutableFloatStateOf(0f)
        compose.setContent { DearbyTheme { DearbySearchField(query, { query = it }, "명함 검색", expansion = expansion, onExpand = { expansion = 1f }) } }
        compose.onNodeWithContentDescription("검색").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("명함 검색").assertIsFocused().performTextInput("지민")
        expansion = 0f
        compose.onNodeWithContentDescription("명함 검색").assertExists()
        compose.onNodeWithContentDescription("검색").assertDoesNotExist()
    }

    @Test fun pullingListTopRevealsSearchAndScrollingUpCollapsesIt() {
        compose.setContent { DearbyTheme { Column {
            val reveal = rememberDearbySearchReveal()
            DearbySearchField(query, { query = it }, "명함 검색", expansion = reveal.expansion, onExpand = reveal::expand)
            LazyColumn(Modifier.nestedScroll(reveal.connection).testTag("list")) { items(40) { Text("항목 $it", Modifier.height(56.dp)) } }
        } } }
        compose.onNodeWithContentDescription("검색").assertExists()
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = top + 400f, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("명함 검색").assertExists()
        compose.onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom - 10f, endY = bottom - 400f, durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("검색").assertExists()
    }

    @Test fun groupHeaderTogglesAndAnnouncesState() {
        compose.setContent { DearbyTheme { Box(Modifier.testTag("gallery")) { ReceivedCardGroupsSample() } } }
        val header = compose.onNodeWithContentDescription("Dearby 개발자 컨퍼런스, 2개")
        header.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "펼침")).assert(isHeading())
        compose.onNodeWithText("이서연").assertExists()
        capture("android-received-groups")
        header.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "접힘"))
        compose.onNodeWithText("이서연").assertDoesNotExist()
        compose.onNodeWithText("최유나").assertExists()
    }

    @Test fun readingStateKeepsLabels() {
        show(editing = false)
        compose.onNodeWithContentDescription("이름, 김지민").assertExists()
        capture("android-controls-read")
    }
}
