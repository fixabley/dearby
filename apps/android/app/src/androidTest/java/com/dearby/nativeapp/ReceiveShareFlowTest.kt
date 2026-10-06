package com.dearby.nativeapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.dearby.nativeapp.app.ApiOrigin
import com.dearby.nativeapp.app.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Scanning a Dearby QR opens the shared card without sign-in; the camera is replaced by the Debug scan text. */
class ReceiveShareFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val server = FixtureServer()
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun scan(text: String) {
        ApiOrigin.debugScanText = text
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onAllNodesWithText("QR").onLast().performClick()
        compose.onNodeWithText("QR 찍기").performClick()
    }

    @Before fun pointAtFixture() { ApiOrigin.debugOverride = server.origin }
    @After fun reset() { scenario?.close(); server.close(); ApiOrigin.debugOverride = null; ApiOrigin.debugScanText = null }

    @Test fun scannedShareOpensTheCardWithItsActivities() {
        scan("${BuildConfig.WEB_ORIGIN}/s/${FixtureServer.SHARE_ID}")
        waitFor("이서연")
        compose.onNodeWithText("함께 공유된 활동").assertIsDisplayed()
        compose.onNodeWithText("테스트 컨퍼런스").assertIsDisplayed()
        assertFalse(server.requests.any { "/v1/auth" in it })
        capturePrototype(compose, "received-share")
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("QR 찍기").assertIsDisplayed()
    }
    @Test fun legacyCardCodeOpensWithoutActivitiesAndForeignCodesAreRejected() {
        scan("dearby://card/${FixtureServer.SHARED_CARD_ID}")
        waitFor("이서연")
        compose.onNodeWithText("함께 공유된 활동").assertDoesNotExist()
        scenario?.close()
        scan("https://example.test/not-dearby")
        waitFor("Dearby 명함 QR이 아니에요.")
    }
}
