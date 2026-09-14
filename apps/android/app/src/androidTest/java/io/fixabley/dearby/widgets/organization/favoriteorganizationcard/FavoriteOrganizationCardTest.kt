package io.fixabley.dearby.widgets.organization.favoriteorganizationcard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.data.NoticeSnapshotReader
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoriteOrganizationCardTest {
    private fun session(catalog: io.fixabley.dearby.app.data.NoticeSnapshot, id: String): NoticeSession {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = setOf(id); override fun write(ids: Set<String>) {} })
        return NoticeSession(NoticeSnapshotReader { catalog }, favorites).also { it.load() }
    }

    @get:Rule val rule = createComposeRule()

    @Test
    fun linkedNoticeAndRemovalDelegateTheirExactIds() {
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val state = session(catalog, "krc").favoriteStates().single()
        val notices = catalog.notices.filter { it.organizationId == state.id }
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        rule.setContent {
            DearbyTheme {
                FavoriteOrganizationCard(state, onRemove = { removed.add(it) }, showDetail = { opened.add(it) })
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
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val state = session(catalog, "cbnu-career").favoriteStates().single()
        var removed: String? = null
        rule.setContent {
            DearbyTheme {
                FavoriteOrganizationCard(state, onRemove = { removed = it }, showDetail = {})
            }
        }
        rule.onNodeWithText("충북대학교").assertIsDisplayed()
        rule.onNodeWithText("현재 연결된 공고가 없어요").assertIsDisplayed()
        rule.onNodeWithTag("remove.cbnu-career").performClick()
        rule.runOnIdle { assertEquals("cbnu-career", removed) }
    }
}
