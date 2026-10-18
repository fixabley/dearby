package io.wid.dearby.domain.collection

import io.wid.dearby.domain.ScheduleKind

import java.time.LocalDate
import java.time.OffsetDateTime

// 수집한 개별 일정. 시각이 있으면 startAt·endAt, 날짜만 있으면 startDate·endDate(종일), 둘 다 모르면 dateLabel만
data class CollectedSchedule(
    val title: String,
    val kind: ScheduleKind,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val dateLabel: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)
