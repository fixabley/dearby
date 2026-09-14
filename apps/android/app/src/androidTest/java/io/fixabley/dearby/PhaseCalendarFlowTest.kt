package io.fixabley.dearby

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.NoticeSession
import io.fixabley.dearby.app.DearbyApp
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.app.data.NoticeSnapshotReader
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
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val contest = catalog.notices.single { it.id == "cbnu-software-1154064" }
        val captured = mutableListOf<CalendarDraft>()
        val repository = NoticeSession(NoticeSnapshotReader { catalog.copy(notices = listOf(contest)) }, favorites)
        rule.setContent { DearbyTheme { DearbyApp(repository, {}, {}, { captured.add(it) }) } }
        rule.waitForCatalog()
        rule.onNodeWithTag("details.${contest.id}").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.phase.0"))
        rule.runOnIdle { assertTrue(captured.isEmpty()) }
        rule.onNodeWithTag("schedule.title.0").assertTextEquals("온라인 예선")
        rule.onNodeWithTag("calendar.phase.0").assertContentDescriptionEquals("온라인 예선 캘린더에 추가").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.phase.1"))
        rule.onNodeWithTag("schedule.title.1").assertTextEquals("결선 진출 발표")
        rule.onNodeWithTag("calendar.phase.1").assertContentDescriptionEquals("결선 진출 발표 캘린더에 추가").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("calendar.phase.2"))
        rule.onNodeWithTag("schedule.title.2").assertTextEquals("결선·시상")
        rule.onNodeWithTag("calendar.phase.2").assertContentDescriptionEquals("결선·시상 캘린더에 추가").performClick()
        rule.runOnIdle {
            assertEquals(3, captured.size)
            assertEquals("온라인", captured[0].location)
            assertTrue(captured[0].title.endsWith("[예선]"))
            assertEquals("2026 부산 사이버보안 콘퍼런스 행사장", captured[2].location)
            assertTrue(captured[2].title.endsWith("[결선·시상]"))
            assertTrue(captured[1].title.endsWith("[결선 진출 발표]"))
            assertEquals(listOf("2026-10-14", "2026-10-22", "2026-11-04"), captured.map {
                java.time.Instant.ofEpochMilli(it.beginsAtMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
            })
            assertTrue(captured.all { it.allDay })
            assertTrue(captured.all { it.description == contest.sourceURL })
        }
        rule.onNodeWithTag("notice.detail").assertIsDisplayed()
    }

    @Test fun malformedPhaseKeepsExistingDisplayButHasNoCalendarButton() {
        val catalog = AssetNoticeSnapshotReader(InstrumentationRegistry.getInstrumentation().targetContext.assets).load()
        val first = catalog.notices.first()
        val invalid = first.copy(schedules = listOf(first.schedules.first().copy(startsAt = "invalid", startsOn = null)))
        val repository = NoticeSession(NoticeSnapshotReader { catalog.copy(notices = listOf(invalid)) }, favorites)
        rule.setContent { DearbyTheme { DearbyApp(repository, {}, {}, { fail("No phase action") }) } }
        rule.waitForCatalog()
        rule.onNodeWithTag("details.${first.id}").performClick()
        rule.onNodeWithTag("notice.detail").performScrollToNode(hasTestTag("schedule.date.0"))
        rule.onNodeWithTag("schedule.date.0").assertContentDescriptionContains(invalid.schedules.first().summary, substring = true)
        rule.onNodeWithTag("calendar.phase.0").assertDoesNotExist()
    }
}
