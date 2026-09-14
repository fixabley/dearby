package io.fixabley.dearby

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.pages.noticedetail.model.NoticeScheduleState
import io.fixabley.dearby.pages.noticedetail.ui.NoticeScheduleSection
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import org.junit.Rule
import org.junit.Test

class DetailTimezoneTest {
    @get:Rule val rule = createComposeRule()
    @Test fun hidesOnlyKoreanTimezoneAndRetainsDateTimeStructure() {
        var zone by mutableStateOf("Asia/Seoul")
        rule.setContent { DearbyTheme {
            NoticeScheduleSection(NoticeScheduleState(NoticePhase("event", startsAt = "2026-09-15T14:00:00+09:00",
                endsAt = "2026-09-15T16:00:00+09:00", timezone = zone), emptyList()), null, 0, {})
        } }
        rule.onNodeWithText("한국 시간").assertDoesNotExist()
        rule.onNodeWithTag("schedule.date.0.0").assertContentDescriptionContains("시작 시각: 2026-09-15T14:00:00+09:00", substring = true)
        rule.runOnIdle { zone = "Europe/London" }
        rule.onNodeWithText("Europe/London").assertExists()
    }
}
