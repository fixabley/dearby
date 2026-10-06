package com.dearby.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.ApiOrigin
import com.dearby.nativeapp.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** `/s/<UUID>` App Links open the shared card; other paths on the web origin are ignored. */
class LinkRoutingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val server = FixtureServer()
    private fun open(link: String) = ActivityScenario.launch<MainActivity>(
        Intent(Intent.ACTION_VIEW, Uri.parse(link), InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java))

    @Before fun pointAtFixture() { ApiOrigin.debugOverride = server.origin }
    @After fun reset() { server.close(); ApiOrigin.debugOverride = null }

    @Test fun sharedCardLinkOpensTheSharedCard() {
        open("${BuildConfig.WEB_ORIGIN}/s/${FixtureServer.SHARE_ID}").use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("이서연").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("공유 명함").assertIsDisplayed()
            compose.onNodeWithText("테스트 컨퍼런스").assertIsDisplayed()
        }
    }
    @Test fun otherLinksAreIgnored() {
        open("${BuildConfig.WEB_ORIGIN}/cards/d0000000-0000-4000-8000-000000000001").use {
            compose.onNodeWithText("활동 둘러보기").assertIsDisplayed()
            compose.onNodeWithText("공유 명함").assertDoesNotExist()
        }
    }
}
