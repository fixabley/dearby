package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.pages.catalog.*
import com.dearby.nativeapp.shared.ui.DearbyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI fixtures only: these records never enter production defaults or API integration tests. */
class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    private val activity = ActivityState("a", "p", "o", "활동 UI 검증용 컨퍼런스", "기술과 사람", "테스트 조직", "함께 배우고 교류하는 행사입니다.", true, "모집 중", "선발형 · 신청 후 선정 필요", "2026년 10월 · 시간 확인되지 않음", listOf("참가 대상" to "개발에 관심 있는 사람", "비용" to "확인되지 않음", "장소" to "확인되지 않음"), "https://example.org", "https://example.org/apply", "2026년 9월 27일 09:00 KST", "UI 검증 전용 자료", false, false, null)
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { (if (name.endsWith("report")) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun largeTextKeepsBannerAndStatusActionReachable() {
        var edits = 0
        compose.setContent { DearbyTheme {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f)) {
                ActivityDetailPage(activity.copy(report = "applied"), false, true, null, {}, {}, {}, {}, {}, { edits++ })
            }
        } }
        compose.onNodeWithText("이 활동은 이미 신청한 활동이에요.").assertIsDisplayed()
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        assertEquals(1, edits)
        capture("fixture-catalog-large-text")
    }
    @Test fun registeredDirectAndNoneKeepExclusiveContext() {
        var id: String? by mutableStateOf(null); var label by mutableStateOf("")
        compose.setContent { DearbyTheme {
            com.dearby.nativeapp.widgets.activity.contextPicker.ActivityContextPicker(listOf(com.dearby.nativeapp.widgets.activity.contextPicker.ActivityChoiceState("past-id", "과거 활동")), id, label) { selected, text -> id = selected; label = text }
        } }
        compose.onNodeWithText("등록 활동").performClick()
        compose.onNodeWithText("과거 활동").performClick()
        assertEquals("past-id", id); assertEquals("", label)
        compose.onNodeWithText("직접 입력").performClick()
        compose.onNodeWithText("활동 이름").performTextInput("직접 쓴 활동")
        assertNull(id); assertEquals("직접 쓴 활동", label)
        compose.onNodeWithText("선택 안 함").performClick()
        assertNull(id); assertEquals("", label)
        capture("fixture-context-picker")
    }
    @Test fun discoveryOnlyCurrentAndSavedIncludesClosed() {
        var saved by mutableStateOf(false)
        val closed = activity.copy(id = "closed", title = "종료된 활동", current = false, status = "모집 종료", programSaved = true)
        compose.setContent { DearbyTheme { CatalogPage(CatalogState(activities = listOf(activity, closed), storageReady = true), saved, {}, {}, {}) } }
        compose.onNodeWithText(activity.title).assertExists()
        compose.onNodeWithText("종료된 활동").assertDoesNotExist()
        capture("fixture-catalog-discovery")
        compose.runOnIdle { saved = true }
        compose.onNodeWithText("종료된 활동").assertExists()
        capture("fixture-catalog-saved")
    }
    @Test fun detailBannerFixedSourceNeverReportsAndUnknownIsHonest() {
        var sources = 0; var reports = 0
        compose.setContent { DearbyTheme { ActivityDetailPage(activity.copy(report = "applied"), false, true, null, {}, {}, {}, { sources++ }, {}, { reports++ }) } }
        compose.onNodeWithText("이 활동은 이미 신청한 활동이에요.").assertIsDisplayed()
        capture("fixture-catalog-applied")
        compose.onNodeWithText("공식 사이트 보기").performScrollTo().performClick()
        assertEquals(1, sources); assertEquals(0, reports)
        compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
        assertEquals(1, reports)
        compose.onNodeWithText("이 활동은 이미 신청한 활동이에요.").assertIsDisplayed()
    }
    @Test fun reportRequiresExplicitAnswerAndLaterDoesNotWrite() {
        var value: String? = null; var later = 0
        compose.setContent { DearbyTheme { ApplicationReportDialog(false, null, { value = it }, { later++ }) } }
        capture("fixture-catalog-report")
        compose.onNodeWithText("나중에").performClick()
        assertNull(value); assertEquals(1, later)
        compose.onNodeWithText("신청하지 않았어요").performClick(); assertEquals("not_applied", value)
        compose.onNodeWithText("신청했어요").performClick(); assertEquals("applied", value)
    }
    @Test fun failedRefreshAndLoadingAreDistinctFromEmpty() {
        var state by mutableStateOf(CatalogState(loading = true))
        compose.setContent { DearbyTheme { CatalogPage(state, false, {}, {}, {}) } }
        compose.onNodeWithText("불러오는 중…").assertIsNotEnabled()
        compose.runOnIdle { state = CatalogState() }
        compose.onNodeWithText("현재 모집 중으로 확인된 활동이 없습니다.").assertExists()
        capture("fixture-catalog-empty")
        compose.runOnIdle { state = state.copy(error = "연결 실패", cached = true) }
        compose.onNodeWithText("연결 실패").assertExists()
        compose.onNodeWithText("현재 모집 중으로 확인된 활동이 없습니다.").assertDoesNotExist()
        capture("fixture-catalog-error")
    }
}
