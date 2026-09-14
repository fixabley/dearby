package io.fixabley.dearby.widgets.favoriteorganizationcard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.entities.noticecatalog.api.AssetCatalogProvider
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.widgets.favoriteorganizationcard.ui.FavoriteOrganizationCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoriteOrganizationCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun linkedNoticeAndRemovalDelegateTheirExactIds() {
        val catalog = AssetCatalogProvider(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val organization = catalog.organization("krc")!!
        val notices = catalog.feed.filter { it.organizationId == organization.id }
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        rule.setContent {
            DearbyTheme {
                FavoriteOrganizationCard(organization, emptyList(), notices,
                    notices.associate { it.id to catalog.contextNames(it) },
                    onRemove = { removed.add(it) }, showDetail = { opened.add(it.id) })
            }
        }
        rule.onNodeWithText("채용 › 채용행사 · 충북대학교", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("favorite.notice.${notices.single().id}").performClick()
        rule.onNodeWithTag("remove.krc").performClick()
        rule.runOnIdle {
            assertEquals(listOf(notices.single().id), opened)
            assertEquals(listOf("krc"), removed)
        }
    }

    @Test
    fun legacyOrganizationWithoutNoticesKeepsItsParentAndDeleteAction() {
        val catalog = AssetCatalogProvider(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val organization = catalog.organization("cbnu-career")!!
        var removed: String? = null
        rule.setContent {
            DearbyTheme {
                FavoriteOrganizationCard(organization, catalog.organizationPath(organization.id).dropLast(1),
                    emptyList(), emptyMap(), onRemove = { removed = it }, showDetail = {})
            }
        }
        rule.onNodeWithText("충북대학교").assertIsDisplayed()
        rule.onNodeWithText("현재 연결된 공고가 없어요").assertIsDisplayed()
        rule.onNodeWithTag("remove.cbnu-career").performClick()
        rule.runOnIdle { assertEquals("cbnu-career", removed) }
    }
}
