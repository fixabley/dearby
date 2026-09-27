package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.dearby.nativeapp.features.qr.QrActions
import com.dearby.nativeapp.pages.qr.*
import com.dearby.nativeapp.pages.profile.*
import com.dearby.nativeapp.pages.wallet.*
import com.dearby.nativeapp.shared.ui.DearbyTheme
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.features.contact.contactAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated UI fixture tests. These people are never bundled in the production application. */
class ScreenTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardState("00000000-0000-0000-0000-000000000001", "owner", "테스트 사용자", "Android 개발자", "컨퍼런스 명함", "만나서 반가워요", "UI 검사 전용 정보", listOf(contactAction("email", "email", "이메일", "fixture@example.invalid")), listOf(CardHistoryState("history", "테스트 컨퍼런스", "발표자", "2026-09-27", null, "")))
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun importStartsEmptyAndSendsOnlyChosenIds() {
        var sent = emptySet<String>()
        compose.setContent { DearbyTheme { ImportPage(listOf(ImportEntryState("a", "테스트 사용자", "Android 개발자", "컨퍼런스"), ImportEntryState("b", "다른 사용자", "디자이너", "")), false, { sent = it }, {}) } }
        compose.onNodeWithText("선택한 명함 0개 가져오기").assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[0].performClick()
        screenshot("fixture-import")
        compose.onNodeWithText("선택한 명함 1개 가져오기").performClick()
        assertEquals(setOf("a"), sent)
    }
    @Test fun selectingCardNeverAutomaticallySends() {
        var sends = 0
        compose.setContent { DearbyTheme { SendPage(listOf(card), card.id, "받는 사람", false, {}, { _, _, _ -> sends++ }, {}, {}, {}) } }
        compose.waitForIdle(); assertEquals(0, sends)
        screenshot("fixture-send-picker")
        compose.onNodeWithText("이 명함 보내기").performClick()
        assertEquals(1, sends)
    }
    @Test fun publicSelectionStartsPrivateAndSelectAllCanBeReversed() {
        var selection: PublishSelectionState? = null
        compose.setContent { DearbyTheme { CardEditor(CardEditorState(listOf(VisibilityChoiceState("contact", "이메일")), listOf(VisibilityChoiceState("history", "발표"))), false, { selection = it }, {}, {}) } }
        compose.onNodeWithText("명함 이름").performTextInput("검사 명함")
        compose.onAllNodes(isToggleable()).assertAll(isOff())
        compose.onNodeWithText("전체 선택").performClick()
        compose.onAllNodes(isToggleable()).assertAll(isOn())
        compose.onNodeWithText("전체 해제").performClick()
        compose.onNodeWithText("선택한 정보로 명함 게시").performScrollTo().performClick()
        assertTrue(selection!!.contactIds.isEmpty()); assertTrue(selection!!.historyIds.isEmpty())
    }
    @Test fun reciprocalOnlyWalletHidesGroupLabelsAndSearchesActivity() {
        compose.setContent { DearbyTheme { WalletPage(listOf(WalletEntryState("r", card, "Android 컨퍼런스", "2026-09-27", true)), true, 0, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("내 명함을 주지 않은 상대").assertDoesNotExist()
        compose.onNodeWithText("서로 주고받은 상대").assertDoesNotExist()
        compose.onNodeWithText("이름·직무·활동 검색").performTextInput("컨퍼런스")
        compose.onNodeWithText("테스트 사용자").assertIsDisplayed()
        screenshot("fixture-wallet")
    }
    @Test fun qrShowsShareMenuAndCreateInvitation() {
        var selected by mutableStateOf<String?>(card.id)
        val link = QrActions.link(card.id, ExchangeContextModel())
        compose.setContent { DearbyTheme { QrPage(listOf(card), selected, QrActions.bitmap(link).asImageBitmap(), { selected = it }, {}, {}, {}, {}, {}, {}, "", {}, {}) } }
        screenshot("fixture-qr")
        compose.onNodeWithContentDescription("공유 메뉴").performClick()
        compose.onNodeWithText("QR 이미지 저장").assertIsDisplayed()
        compose.onNodeWithText("링크 복사").performClick()
        compose.onNodeWithText("＋\n새 명함").performScrollTo().performClick()
        compose.onNodeWithText("새 명함 만들기").assertIsDisplayed()
    }
    @Test fun cardHeaderStaysPutAndContactsAreActionable() {
        var expanded by mutableStateOf(false)
        var opened = ""
        compose.setContent { DearbyTheme { Box(Modifier.fillMaxSize().padding(20.dp)) { CardContent(card, expanded, { expanded = !expanded }, Modifier.fillMaxWidth(), { opened = it.id }) } } }
        val before = compose.onNodeWithText(card.person).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("상세보기").performClick()
        assertEquals(before, compose.onNodeWithText(card.person).fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("이메일").performClick()
        assertEquals("email", opened)
        screenshot("fixture-card-expanded")
    }
    @Test fun guestProfileRequiresLogin() {
        compose.setContent { DearbyTheme { ProfilePage(ProfileState("", "", "", emptyList(), emptyList()), false, false, {}, {}, {}, {}) } }
        compose.onNodeWithText("이메일로 로그인").assertIsDisplayed()
        compose.onNodeWithText("편집").assertDoesNotExist()
        compose.onNodeWithText("연락처 / 활동 이력 추가").assertDoesNotExist()
    }
    @Test fun profileDetailHasNoCreateCardCta() {
        compose.setContent { DearbyTheme { ProfilePage(ProfileState("테스트 사용자", "Android 개발자", "검증용 프로필입니다", listOf(ContactState("a", "email", "이메일", "fixture@example.invalid")), listOf(HistoryState("h", "컨퍼런스", "발표자", "2026-09-27"))), false, true, {}, {}, {}, {}) } }
        compose.onNodeWithText("새 명함 만들기").assertDoesNotExist()
        screenshot("fixture-profile")
    }
}
