package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.data.NoticeSnapshotReader
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoticeCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun rendersSuppliedSavedValueAndDelegatesAllActions() {
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val notice = catalog.notices.first { it.organizationId == "krc" }
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val session = NoticeSession(NoticeSnapshotReader { catalog }, favorites)
        kotlinx.coroutines.runBlocking { session.load() }
        val multiplier = mutableStateOf(1)
        var saves = 0
        var details = 0
        rule.setContent {
            DearbyTheme {
                val currentMultiplier = multiplier.value
                NoticeCard(session.cardStates().first { it.id == notice.id }, "1 / 4", save = { saves += currentMultiplier }, showDetail = { details++ })
            }
        }
        rule.onNodeWithTag("classification.${notice.id}", useUnmergedTree = true)
            .assertTextEquals("채용 › 채용행사 · 충북대학교")
        rule.onNodeWithTag("save.${notice.id}").performClick()
        // The callback does not imply an internal store or optimistic widget state.
        rule.onNodeWithTag("save.${notice.id}").assertContentDescriptionEquals("한국농어촌공사 저장")
        rule.runOnIdle { favorites.save("krc"); multiplier.value = 10 }
        rule.onNodeWithTag("save.${notice.id}").assertContentDescriptionEquals("저장됨 · 한국농어촌공사").assertIsSelected()
        rule.onNodeWithTag("activity.${notice.id}").performTouchInput { doubleClick() }
        rule.onNodeWithTag("details.${notice.id}").performClick()
        rule.runOnIdle {
            assertEquals(11, saves)
            assertEquals(1, details)
        }
    }
}
