package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.dearby.nativeapp.features.contact.contactAction
import com.dearby.nativeapp.features.qr.QrActions
import com.dearby.nativeapp.pages.profile.*
import com.dearby.nativeapp.pages.qr.*
import com.dearby.nativeapp.pages.wallet.*
import com.dearby.nativeapp.shared.ui.DearbyTheme
import com.dearby.nativeapp.widgets.card.cardContent.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Rich visual fixtures live ONLY in the instrumentation APK, never the app. */
class VisualFidelityTest {
    @get:Rule val compose = createComposeRule()
    private val contacts = listOf(contactAction("email", "email", "이메일", "fixture@example.invalid"), contactAction("github", "github", "GitHub", "dearby-fixture"))
    private val histories = listOf(CardHistoryState("h1", "기술 컨퍼런스", "운영 스태프", "2026-09-01", "2026-09-02", ""), CardHistoryState("h2", "연합동아리", "서비스 기획", "2026-03-01", "2026-08-31", ""), CardHistoryState("h3", "캠퍼스 해커톤", "서비스 기획", "2025-11-01", "2025-11-02", ""))
    private val cards = listOf("김지민" to "서비스 기획", "박서연" to "프로덕트 디자인", "이도윤" to "프론트엔드 개발").mapIndexed { i, (name, job) -> CardState("00000000-0000-0000-0000-00000000000${i + 1}", "owner-$i", name, job, listOf("네트워킹", "디자인과 협업", "커뮤니티")[i], "새로운 인연에게 나를 소개해요.", "사람을 연결하는 경험을 만듭니다.", contacts, histories) }
    private val ownCards = cards.map { it.copy(person = cards.first().person, job = cards.first().job, ownerId = cards.first().ownerId) }
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        File(folder, "visual-$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun content(large: Boolean = false, block: @Composable () -> Unit) = compose.setContent { DearbyTheme { val density = LocalDensity.current; CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else density.fontScale)) { Surface(Modifier.fillMaxSize()) { block() } } } }
    @Test fun walletThreeCardsAndSwipe() {
        content { WalletPage(cards.mapIndexed { i, card -> WalletEntryState("r$i", card, "기술 컨퍼런스", "2026-09-27", false) }, true, 0, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("김지민").assertIsDisplayed(); capture("wallet")
        compose.onNodeWithTag("walletPager").performTouchInput { swipeUp() }
        compose.waitForIdle(); compose.onNodeWithText("디자인과 협업").assertIsDisplayed(); capture("wallet-swipe")
    }
    @Test fun sendThreeCardsNeverSendsOnSelection() {
        var sent = 0
        content { SendPage(ownCards, cards.first().id, "최유진", false, {}, { _, _, _ -> sent++ }, {}, {}, {}) }
        capture("send-picker"); assertEquals(0, sent)
        compose.onNodeWithTag("sendPager").performTouchInput { swipeUp() }
        compose.onNodeWithText("2 / 3 · 위아래로 넘기기").assertIsDisplayed(); assertEquals(0, sent)
        compose.onNodeWithText("이 명함 보내기").performClick(); assertEquals(1, sent)
    }
    @Test fun sendBusyLocksActions() {
        content { SendPage(ownCards, cards.first().id, "최유진", true, {}, { _, _, _ -> fail("sent while busy") }, { fail("create while busy") }, {}, {}) }
        compose.onNodeWithText("＋ 새 명함").assertIsNotEnabled(); compose.onNodeWithText("이 명함 보내기").assertIsNotEnabled()
    }
    @Test fun profileContactsAndTimeline() {
        content { ProfilePage(ProfileState(cards.first().person, cards.first().job, cards.first().introduction, contacts.map { ContactState(it.id, it.kind, it.label, it.value) }, histories.map { HistoryState(it.id, it.title, it.role, it.startDate, it.endDate) }), false, true, {}, {}, {}, {}) }
        capture("profile")
    }
    @Test fun guestProfile() { content { ProfilePage(ProfileState("", "", "", emptyList(), emptyList()), false, false, {}, {}, {}, {}) }; capture("guest-profile") }
    @Test fun qrAndInvitation() {
        var selected by mutableStateOf<String?>(cards.first().id)
        content { QrPage(cards, selected, QrActions.bitmap(QrActions.link(cards.first().id, ExchangeContextModel())).asImageBitmap(), { selected = it }, {}, {}, {}, {}, {}, {}, "", {}, {}) }
        capture("qr"); compose.onNodeWithText("＋\n새 명함").performScrollTo().performClick(); capture("new-card")
    }
    @Test fun editorPrivateByDefault() {
        content { CardEditor(CardEditorState(contacts.map { VisibilityChoiceState(it.id, it.label, it.kind, it.value) }, histories.map { VisibilityChoiceState(it.id, it.title, detail = it.role, date = it.startDate) }, cards.first().person, cards.first().job, cards.first().introduction), false, {}, {}, {}) }
        compose.onAllNodes(isToggleable()).assertAll(isOff()); capture("editor")
    }
    @Test fun sharedCardRequiresExplicitSave() {
        var saves = 0
        content { SharedCardPage(cards.first(), false, {}, { saves++ }, {}, {}) }
        capture("shared-card"); assertEquals(0, saves); compose.onNodeWithText("카드 저장").performClick(); assertEquals(0, saves); compose.onNodeWithText("이 기기에 저장").performClick(); assertEquals(1, saves)
    }
    @Test fun largeTextSharedCardKeepsSaveReachable() {
        content(true) { SharedCardPage(cards.first(), false, {}, {}, {}, {}) }
        compose.onNodeWithText("카드 저장").assertIsDisplayed(); capture("shared-large-text")
    }
    @Test fun largeTextWalletAndSendControlsStayReachable() {
        content(true) { WalletPage(cards.mapIndexed { i, card -> WalletEntryState("r$i", card, "기술 컨퍼런스", "2026-09-27", false) }, true, 0, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("나도 명함 주기").assertIsDisplayed(); capture("wallet-large-text")
    }

    @Test fun smallScreenLargeTextKeepsEditorPublishReachable() {
        compose.setContent { DearbyTheme { val density = LocalDensity.current; CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) { Surface(Modifier.requiredSize(320.dp, 568.dp)) { CardEditor(CardEditorState(emptyList(), emptyList(), "테스트 사용자", "개발자", "큰 글자 확인"), false, {}, {}, {}) } } } }
        compose.onNodeWithText("명함 이름").performScrollTo().performTextInput("작은 화면")
        compose.onNodeWithText("선택한 정보로 명함 게시").performScrollTo().assertIsDisplayed(); capture("small-large-editor")
    }
}
