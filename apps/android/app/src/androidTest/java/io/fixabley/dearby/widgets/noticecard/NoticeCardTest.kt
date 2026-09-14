package io.fixabley.dearby.widgets.noticecard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.entities.noticecatalog.api.AssetCatalogProvider
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.widgets.noticecard.ui.NoticeCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoticeCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun rendersSuppliedSavedValueAndDelegatesAllActions() {
        val catalog = AssetCatalogProvider(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val notice = catalog.feed.first { it.organizationId == "krc" }
        val saved = mutableStateOf(false)
        var saves = 0
        var details = 0
        rule.setContent {
            DearbyTheme {
                NoticeCard(notice, catalog.organization("krc"), catalog.contextNames(notice),
                    saved.value, "1 / 4", save = { saves++ }, showDetail = { details++ })
            }
        }
        rule.onNodeWithTag("classification.${notice.id}", useUnmergedTree = true)
            .assertTextEquals("채용 › 채용행사 · 충북대학교")
        rule.onNodeWithTag("save.${notice.id}").performClick()
        // The callback does not imply an internal store or optimistic widget state.
        rule.onNodeWithTag("save.${notice.id}").assertTextEquals("한국농어촌공사 저장")
        rule.runOnIdle { saved.value = true }
        rule.onNodeWithTag("save.${notice.id}").assertTextEquals("저장됨 · 한국농어촌공사")
        rule.onNodeWithTag("activity.${notice.id}").performTouchInput { doubleClick() }
        rule.onNodeWithTag("details.${notice.id}").performClick()
        rule.runOnIdle {
            assertEquals(2, saves)
            assertEquals(1, details)
        }
    }
}
