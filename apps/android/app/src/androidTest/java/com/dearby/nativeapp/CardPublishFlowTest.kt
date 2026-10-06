package com.dearby.nativeapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.ApiOrigin
import com.dearby.nativeapp.app.MainActivity
import com.dearby.nativeapp.entities.account.api.SessionVault
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Card publishing against the in-process fixture API, with a session vault of its own. */
class CardPublishFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val server = FixtureServer()
    private val vault = "account.uitest.${UUID.randomUUID()}"
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Before fun pointAtFixture() { ApiOrigin.debugOverride = server.origin; ApiOrigin.debugSessionName = vault }
    @After fun reset() {
        scenario?.close(); server.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SessionVault(context, vault).clear(); context.deleteSharedPreferences(vault)
        ApiOrigin.debugOverride = null; ApiOrigin.debugSessionName = null
    }
    private fun openComposerAndSignIn(code: String = FixtureServer.CODE, markApplied: Boolean = false) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        if (markApplied) {
            waitFor("테스트 컨퍼런스")
            compose.onNodeWithText("테스트 컨퍼런스").performClick()
            compose.onNodeWithText("신청 상태 수정").performScrollTo().performClick()
            compose.onNodeWithText("신청함").performClick()
            compose.onNodeWithContentDescription("뒤로").performClick()
        }
        compose.onAllNodesWithText("QR").onLast().performClick()
        compose.onNodeWithText("명함 만들기").performClick()
        compose.onNodeWithText("로그인하고 명함 발행").assertIsNotEnabled()
        assertEquals(emptyList<String>(), server.requests.filter { "/v1/auth" in it || "/v1/profile" in it })
        compose.onNodeWithContentDescription("이름").performTextInput("김지민")
        compose.onNodeWithText("로그인하고 명함 발행").performClick()
        compose.onNodeWithTag("sign-in-email").performTextInput("me@example.test")
        compose.onNodeWithText("인증번호 받기").performClick()
        waitFor("초 후 다시 받을 수 있어요")
        compose.onNodeWithTag("sign-in-code").performTextInput(code)
        compose.onAllNodesWithText("로그인").filter(hasClickAction()).onFirst().performClick()
    }

    @Test fun composerAsksSignInOnlyOnPublishThenPublishes() {
        openComposerAndSignIn("000000")
        waitFor("인증번호가 맞지 않거나 만료됐어요.")
        capturePrototype(compose, "sign-in-code")
        compose.onNodeWithTag("sign-in-code").performTextReplacement(FixtureServer.CODE)
        compose.onAllNodesWithText("로그인").filter(hasClickAction()).onFirst().performClick()
        waitFor("명함을 발행했어요")
        capturePrototype(compose, "card-published")
        assertEquals(listOf("POST /v1/auth/challenges", "POST /v1/auth/sessions", "POST /v1/auth/sessions", "GET /v1/profile", "PUT /v1/profile", "POST /v1/cards"),
            server.requests.filter { it != "GET /v1/catalog" })
        compose.onNodeWithText("확인").performClick()
        // Back on the QR tab the new card is shared right away as <web>/s/<share ID>.
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("명함 QR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("명함 QR").assert(SemanticsMatcher("share URL") {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith("/s/${FixtureServer.SHARE_ID}") == true
        })
        assertEquals(listOf("GET /v1/cards", "POST /v1/cards/c1000000-0000-4000-8000-000000000001/shares"), server.requests.takeLast(2))
        capturePrototype(compose, "qr-share")
    }
    @Test fun cardFailureAfterProfileSaveRetriesOnlyTheCard() {
        server.failingCards = true
        openComposerAndSignIn()
        waitFor("프로필은 저장했어요. 명함 발행만 다시 시도해 주세요.")
        server.failingCards = false
        compose.onNodeWithText("명함 발행").performClick()
        waitFor("명함을 발행했어요")
        assertEquals(1, server.requests.count { it == "PUT /v1/profile" })
    }
    @Test fun choosingAnAppliedActivityMakesANewShare() {
        openComposerAndSignIn(markApplied = true)
        waitFor("명함을 발행했어요")
        compose.onNodeWithText("확인").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("명함 QR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("함께 보낼 활동 (선택)").performScrollTo().performClick()
        compose.onNodeWithText("테스트 컨퍼런스").performScrollTo().performClick()
        compose.waitUntil(10_000) { server.requests.count { it.endsWith("/shares") } == 2 }
        compose.onNodeWithText("테스트 컨퍼런스").performClick()
        compose.waitForIdle()
        assertEquals(2, server.requests.count { it.endsWith("/shares") })
    }
}
