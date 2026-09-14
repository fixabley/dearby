package io.fixabley.dearby

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.app.data.NoticeSnapshotReader
import io.fixabley.dearby.entities.notice.model.NoticeApplication
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ApplicationCalendarFlowTest {
    @get:Rule val rule = createComposeRule()
    private val favorites = FavoritesState(object : FavoriteStore {
        override fun read() = emptySet<String>()
        override fun write(ids: Set<String>) = Unit
    })

    @Test fun applicationEditorOpensOnlyOnClickAndKeepsDetailForCancelReturn() {
        val provider = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        val captured = mutableListOf<CalendarDraft>()
        val repository = NoticeSession(provider, favorites)
        rule.setContent { DearbyTheme { DearbyApp(repository, {}, {}, { captured.add(it) }) } }
        rule.onNodeWithTag("details.cieat-NCR000000007344").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.application"))
        rule.runOnIdle { assertTrue(captured.isEmpty()) }
        rule.onNodeWithTag("calendar.application").performClick()
        rule.runOnIdle {
            assertEquals(1, captured.size)
            assertTrue(captured.single().title.endsWith("[신청 기간]"))
            assertTrue(captured.single().description.contains("신청 URL: ${provider.load().notices.first().applicationInformation.url}"))
        }
        rule.onNodeWithTag("notice.detail").assertIsDisplayed()
    }

    @Test fun unknownApplicationDatesKeepSummaryWithoutAnExportAction() {
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val first = catalog.notices.first().copy(applicationInformation = NoticeApplication("신청 일정 미확인"))
        val repository = NoticeSession(NoticeSnapshotReader { catalog.copy(notices = listOf(first)) }, favorites)
        rule.setContent { DearbyTheme { DearbyApp(repository, {}, {}, { fail("No draft") }) } }
        rule.onNodeWithTag("details.${first.id}").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasText("신청 일정 미확인"))
        rule.onNode(hasText("신청 일정 미확인") and hasAnyAncestor(hasTestTag("notice.detail"))).assertIsDisplayed()
        rule.onNodeWithTag("calendar.application").assertDoesNotExist()
    }
}
