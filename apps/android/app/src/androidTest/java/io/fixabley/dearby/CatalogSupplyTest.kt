package io.fixabley.dearby

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.core.data.CatalogProvider
import io.fixabley.dearby.core.data.FavoriteStore
import io.fixabley.dearby.core.model.ActivityCatalog
import io.fixabley.dearby.core.model.Organization
import io.fixabley.dearby.core.state.FavoritesState
import io.fixabley.dearby.ui.theme.DearbyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogSupplyTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun injectedProviderCanRetryWithoutReplacingFavoritesOrReloadingOnTabChange() {
        var loads = 0
        val provider = CatalogProvider {
            loads++
            check(loads > 1) { "First load fails" }
            ActivityCatalog("2026-09-14", listOf(Organization("legacy", "이전 조직", null)), emptyList())
        }
        val favorites = FavoritesState(object : FavoriteStore {
            override fun read() = setOf("legacy")
            override fun write(ids: Set<String>) = Unit
        })
        rule.setContent { DearbyTheme { DearbyApp(provider, favorites, onOpenSource = {}) } }
        rule.onNodeWithText("공고를 불러오지 못했어요").assertIsDisplayed()
        rule.onNodeWithText("다시 시도").performClick()
        rule.onNodeWithText("표시할 공고가 없어요").assertIsDisplayed()
        rule.onNodeWithTag("tab.favorites").performClick()
        rule.onNodeWithText("이전 조직").assertIsDisplayed()
        rule.onNodeWithText("현재 연결된 공고가 없어요").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(2, loads)
            assertEquals(setOf("legacy"), favorites.ids)
        }
    }
}
