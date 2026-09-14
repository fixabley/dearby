package io.fixabley.dearby

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.app.openVenueMap
import io.fixabley.dearby.entities.activitycatalog.api.AssetCatalogProvider
import io.fixabley.dearby.entities.activitycatalog.model.*
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.pages.noticedetail.ui.NoticeLocationSection
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VenueMapFlowTest {
    @get:Rule val rule = createComposeRule()
    private val first = ActivityVenue(null, "첫 장소 & #", null, VenueCoordinates(0.0, 0.0))
    private val second = ActivityVenue(null, "둘째 장소", null, VenueCoordinates(12.0, 34.0))

    @Test fun multipleVenuesExposeOnlyValidActionsAndPreserveSummary() {
        val selected = mutableListOf<ActivityVenue>()
        val location = ActivityLocation("별관 5층 501호", "offline", "confirmed", listOf(
            first, second, first.copy(coordinates = null), first.copy(coordinates = VenueCoordinates(Double.NaN, 0.0)),
        ))
        rule.setContent { DearbyTheme { NoticeLocationSection(location, { selected.add(it) }) } }
        rule.onNodeWithText(location.summary).assertIsDisplayed()
        rule.onNodeWithTag("venue.map.0").assertIsDisplayed()
        rule.onNodeWithTag("venue.map.1").assertIsDisplayed()
        rule.onNodeWithTag("venue.map.2").assertDoesNotExist()
        rule.onNodeWithTag("venue.map.3").assertDoesNotExist()
        rule.runOnIdle { assertTrue(selected.isEmpty()) }
        rule.onNodeWithTag("venue.map.1").performClick()
        rule.runOnIdle { assertEquals(listOf(second), selected) }
        rule.onNodeWithText("층·호실은 장소 안내를 확인해 주세요.").assertIsDisplayed()
    }

    @Test fun onlineLocationNeverOffersMapEvenIfCoordinatesExist() {
        rule.setContent { DearbyTheme {
            NoticeLocationSection(ActivityLocation("온라인 참여", "online", "confirmed", listOf(first)), { error("Unexpected action") })
        } }
        rule.onNodeWithText("온라인 참여").assertIsDisplayed()
        rule.onNodeWithTag("venue.map.0").assertDoesNotExist()
        rule.onNodeWithText("층·호실은 장소 안내를 확인해 주세요.").assertDoesNotExist()
    }

    @Test fun discoveryDetailPassesCanonicalVenueToAppOnlyAfterExplicitClick() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AssetCatalogProvider(context.assets)
        val expected = provider.load().feed.first().location.venues.first()
        val requests = mutableListOf<android.content.Intent>()
        val favorites = FavoritesState(object : FavoriteStore {
            override fun read() = emptySet<String>()
            override fun write(ids: Set<String>) = Unit
        })
        rule.setContent { DearbyTheme {
            DearbyApp(provider, favorites, onOpenSource = {}, onAddToCalendar = {}, onOpenMap = { venue ->
                assertEquals(expected, venue)
                openVenueMap(venue, { requests.add(it) }, { error("Unexpected unavailable") })
            })
        } }
        rule.onNodeWithTag("details.cieat-NCR000000007344").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("venue.map.0"))
        rule.runOnIdle { assertTrue(requests.isEmpty()) }
        rule.onNodeWithTag("venue.map.0").performClick()
        rule.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals("geo", requests.single().data?.scheme)
        }
        rule.onNodeWithTag("notice.detail").assertIsDisplayed()
    }
}
