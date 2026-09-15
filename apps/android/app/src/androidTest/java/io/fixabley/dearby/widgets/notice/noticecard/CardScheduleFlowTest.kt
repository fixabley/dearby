package io.fixabley.dearby.widgets.notice.noticecard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.*
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.entities.notice.model.*
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.waitForCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CardScheduleFlowTest {
    @get:Rule val rule = createComposeRule()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Allow SurfaceFlinger to present the already-idle Compose frame before capturing pixels.
        android.os.SystemClock.sleep(300)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "card-schedule-$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun canonicalCardMapReachesAppGeoAdapterWithoutOpeningDetail() {
        val reader = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets)
        val expected = reader.load().notices.first().location.venues.first()
        val session = NoticeSession(reader, FavoritesState(object : FavoriteStore {
            override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {}
        }))
        val intents = mutableListOf<android.content.Intent>()
        rule.setContent { DearbyTheme { DearbyApp(session, {}, { venue ->
            assertEquals(expected, venue)
            openVenueMap(venue, { intents.add(it) }, { error("unavailable") })
        }, {}) } }
        rule.waitForCatalog()
        rule.onNodeWithText("cieat.cbnu.ac.kr").assertIsDisplayed()
        val tag = "card.schedule.cieat-NCR000000007344.1.map.0"
        rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
        screenshot("canonical-map")
        rule.onNodeWithTag(tag).performClick()
        rule.runOnIdle {
            assertEquals(1, intents.size)
            assertEquals("geo", intents.single().data?.scheme)
            assertTrue(intents.single().data.toString().contains(expected.coordinates!!.latitude.toString()))
        }
        rule.onNodeWithTag("notice.detail").assertDoesNotExist()
    }

    @Test fun compactLargeFontKeepsEveryScheduleAndFullLongLocationReachable() {
        val longPlace = "부산광역시 해운대구 센텀중앙로 123 컨벤션센터 별관 501호 · https://example.org/very/long/path?registration=complete"
        val venue = NoticeVenue("final", "결선", longPlace, VenueCoordinates(35.0, 129.0))
        val rows = listOf(
            CardScheduleState("신청 기간", "시작 미확인 · 2026.10.7(수) (시간 미확인)까지", listOf(CardPlaceState("신청 위치 미확인"))),
            CardScheduleState("온라인 예선", "2026.10.8(목) 09:00부터 · 종료 미확인", listOf(CardPlaceState("온라인 · https://example.org/preliminary"))),
            CardScheduleState("결선 진출 발표", "일정 미확인", listOf(CardPlaceState("장소 미확인"))),
            CardScheduleState("결선·시상", "2026.10.10(토) (시간 미확인)부터 · 종료 미확인", listOf(CardPlaceState(longPlace, venue))))
        var saves = 0; var details = 0; var selected: NoticeVenue? = null
        rule.setContent { DearbyTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.width(360.dp).height(600.dp)) {
                    NoticeCard(NoticeCardState("fixture", "긴 제목도 잘리지 않고 전부 표시하는 일정 카드", "대회", "모든 참여 대상", "신청", "장소", false, "org", "조직", schedules = rows),
                        "1 / 1", { saves++ }, { details++ }, { selected = it })
                }
            }
        } }
        screenshot("large-top")
        rows.forEachIndexed { index, row ->
            rule.onNodeWithText(row.dateText).performScrollTo().assertIsDisplayed()
            screenshot("large-$index-date")
            row.places.forEach { rule.onNodeWithText(it.text).performScrollTo().assertIsDisplayed() }
        }
        rule.onNodeWithTag("card.schedule.fixture.3.map.0").performScrollTo().assertIsDisplayed().performClick()
        screenshot("large-long-location")
        rule.onNodeWithTag("activity.fixture").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        screenshot("large-location-end")
        rule.runOnIdle { assertEquals(venue, selected); assertEquals(0, saves) }
        rule.onNodeWithTag("save.fixture").assertIsDisplayed().performClick()
        rule.onNodeWithTag("details.fixture").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, saves); assertEquals(1, details) }
    }
}
