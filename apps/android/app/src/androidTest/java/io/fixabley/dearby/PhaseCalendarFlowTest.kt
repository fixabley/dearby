package io.fixabley.dearby

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.entities.activitycatalog.api.ActivityDetailRepository
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.entities.activitycatalog.api.AssetCatalogProvider
import io.fixabley.dearby.entities.activitycatalog.api.CatalogProvider
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhaseCalendarFlowTest {
    @get:Rule val rule = createComposeRule()
    private val favorites = FavoritesState(object : FavoriteStore {
        override fun read() = emptySet<String>()
        override fun write(ids: Set<String>) = Unit
    })

    @Test fun contestPreliminaryAndFinalHandoffsUseTheirOwnDatesAndPlaces() {
        val catalog = AssetCatalogProvider(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val contest = catalog.feed.single { it.id == "cbnu-software-1154064" }
        val captured = mutableListOf<CalendarDraft>()
        val repository = ActivityDetailRepository(CatalogProvider { catalog.copy(feed = listOf(contest)) })
        rule.setContent { DearbyTheme { DearbyApp(repository, favorites, {}, {}, { captured.add(it) }) } }
        rule.onNodeWithTag("details.${contest.id}").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.phase.0"))
        rule.runOnIdle { assertTrue(captured.isEmpty()) }
        rule.onNodeWithTag("calendar.phase.0").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.phase.2"))
        rule.onNodeWithTag("calendar.phase.2").performClick()
        rule.runOnIdle {
            assertEquals(2, captured.size)
            assertEquals("온라인", captured[0].location)
            assertTrue(captured[0].title.endsWith("[예선]"))
            assertEquals("2026 부산 사이버보안 콘퍼런스 행사장", captured[1].location)
            assertTrue(captured[1].title.endsWith("[결선·시상]"))
            assertTrue(captured.all { it.allDay })
        }
        rule.onNodeWithTag("notice.detail").assertIsDisplayed()
    }

    @Test fun malformedPhaseKeepsExistingDisplayButHasNoCalendarButton() {
        val catalog = AssetCatalogProvider(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val first = catalog.feed.first()
        val invalid = first.copy(schedule = listOf(first.schedule.first().copy(startsAt = "invalid", startsOn = null)))
        val repository = ActivityDetailRepository(CatalogProvider { catalog.copy(feed = listOf(invalid)) })
        rule.setContent { DearbyTheme { DearbyApp(repository, favorites, {}, {}, { fail("No phase action") }) } }
        rule.onNodeWithTag("details.${first.id}").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasText(invalid.schedule.first().summary))
        rule.onNodeWithText(invalid.schedule.first().summary).assertIsDisplayed()
        rule.onNodeWithTag("calendar.phase.0").assertDoesNotExist()
    }
}
