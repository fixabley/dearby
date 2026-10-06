package com.dearby.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.dearby.nativeapp.app.MainActivity
import org.junit.Rule
import org.junit.Test

class LinkRoutingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun open(link: String) = ActivityScenario.launch<MainActivity>(
        Intent(Intent.ACTION_VIEW, Uri.parse(link), InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java))

    @Test fun sharedCardLinkIsCapturedAndAcknowledged() {
        open("${BuildConfig.WEB_ORIGIN}/s/d0000000-0000-4000-8000-000000000001").use {
            compose.onNodeWithText("공유 명함 링크").assertIsDisplayed()
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithText("공유 명함 링크").assertDoesNotExist()
        }
    }
    @Test fun otherLinksAreIgnored() {
        open("${BuildConfig.WEB_ORIGIN}/cards/d0000000-0000-4000-8000-000000000001").use {
            compose.onNodeWithText("활동 둘러보기").assertIsDisplayed()
            compose.onNodeWithText("공유 명함 링크").assertDoesNotExist()
        }
    }
}
