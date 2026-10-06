package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.dearby.nativeapp.shared.ui.DearbyTheme
import java.time.Instant
import java.time.ZoneId

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 일정 제목은 실제가 아닌 예시다. */
val CalendarOverlapSampleItems = listOf(
    CalendarOverlapItem("1", "본 행사", Instant.parse("2026-10-24T04:00:00Z"), Instant.parse("2026-10-24T08:00:00Z"), "팀 주간 회의", Instant.parse("2026-10-24T05:00:00Z"), Instant.parse("2026-10-24T06:00:00Z")),
    CalendarOverlapItem("2", "본 행사", Instant.parse("2026-10-24T04:00:00Z"), Instant.parse("2026-10-24T08:00:00Z"), "치과 예약", Instant.parse("2026-10-24T07:30:00Z"), Instant.parse("2026-10-24T08:30:00Z")),
)
val CalendarOverlapSampleZone: ZoneId = ZoneId.of("Asia/Seoul")

@Preview(name = "겹침", widthDp = 390) @Composable private fun OverlapsPreview() = DearbyTheme { CalendarOverlapResult(CalendarOverlapDisplay.Overlaps(CalendarOverlapSampleItems), {}, Modifier.background(Color.White), CalendarOverlapSampleZone) }
@Preview(name = "권한 거부", widthDp = 390) @Composable private fun DeniedPreview() = DearbyTheme { CalendarOverlapResult(CalendarOverlapDisplay.Denied, {}, Modifier.background(Color.White)) }
