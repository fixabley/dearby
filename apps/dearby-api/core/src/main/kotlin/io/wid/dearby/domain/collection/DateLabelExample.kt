package io.wid.dearby.domain.collection

import java.time.LocalDate
import java.time.OffsetDateTime

// 운영자가 어드민에서 직접 지정한 기간의 원문 문구 → 날짜 쌍(admin-v1.md "날짜 문구 피드백"). 수집 지시문의 해석 예시로 쓴다.
// slot은 schedules.slot 그대로("APPLICATION"·"ACTIVITY", 세부 일정은 null). endDate는 포함 끝 날짜(저장된 exclusive에서 하루 뺀 것)
data class DateLabelExample(
    val slot: String?,
    val dateLabel: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val programName: String,
    val note: String? = null, // 운영자가 남긴 사유(schedules.curated_note). 지시문 예시에 "(이유: …)"로 붙는다
)
