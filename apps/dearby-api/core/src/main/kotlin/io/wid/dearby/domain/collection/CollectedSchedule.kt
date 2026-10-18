package io.wid.dearby.domain.collection

import io.wid.dearby.domain.ScheduleKind

import java.time.OffsetDateTime

// 수집한 개별 일정. 시각을 모르면 startAt·endAt이 없고 dateLabel만 있다
data class CollectedSchedule(
    val title: String,
    val kind: ScheduleKind,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val dateLabel: String,
)
